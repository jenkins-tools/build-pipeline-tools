node('master_pipeline') {
   stage 'Parse build job info'
   def web_root = "http://webos-ci.lge.com/download/"
   def job_name = "${BUILD_JOB_NAME}"
   def build_number = "${BUILD_JOB_NUMBER}"
   def job_name_arr = job_name.tokenize('-')
   def branch_name  = job_name_arr[1]
   def machine_name = job_name_arr[3]
   def parsed_info = [
        "Job Name": job_name,
        "Branch Name": branch_name,
        "Build Machine": machine_name,
        "Build Number": build_number
    ]
   echo parsed_info
   git branch: 'dev', url: 'http://mod.lge.com/hub/tv_scm_tool/compare_foss_diff.git'

   stage 'Check foss change'
   sh "python compare_foss_diff.py --jobname ${BUILD_JOB_NAME} --buildnumber ${BUILD_JOB_NUMBER} > compare_result"
   sh "cat compare_result"
   def compare_result = readFile 'compare_result'
   if ( compare_result == "CHANGED\n" ) {
       echo "FOSS: " + compare_result
       stage 'Call a clean engineering build'
       echo 'Clean Build'
       def clean_job_name = "clean-engineering-starfish-" + machine_name + "-build"
       def build_starfish_machine = "Build_starfish_" + machine_name
       join = parallel([clean: {
            node('verification'){
                build job:clean_job_name, parameters: [
                    [$class: 'StringParameterValue',  name:'build_codename',        value:branch_name],
                    [$class: 'StringParameterValue',  name:'token',                 value:'trigger_clean_build'],
                    [$class: 'StringParameterValue',  name:'extra_images',          value:'starfish-bdk'],
                    [$class: 'StringParameterValue',  name:'build_starfish_commit', value:'builds/' + branch_name + '/' + build_number],
                    [$class: 'TextParameterValue',    name:'webos_local',           value:'WEBOS_DISTRO_BUILD_ID="318"\nSDKMACHINE="i686"'],
                    [$class: 'StringParameterValue',  name:'Build_summary',         value:'test bdk build'],
                    [$class: 'BooleanParameterValue', name:build_starfish_machine,  value:true],
                    [$class: 'BooleanParameterValue', name:'region_default',        value:false],
                    [$class: 'BooleanParameterValue', name:'region_atsc',           value:false],
                    [$class: 'BooleanParameterValue', name:'region_arib',           value:false],
                    [$class: 'BooleanParameterValue', name:'region_dvb',            value:false],
                ]
            }
        }
        ])

        def clean_build_result = join.clean.result
        def clean_build_number = join.clean.number.toString()

        stage 'Copy bdk result'
        node('verification'){
            def bdk_job_name = "starfish-bdk"
            def org_dir = '/binary/build_results/starfish_verifications/' + clean_job_name + '/' + clean_build_number
            def target_root = '/binary/build_results/starfish/' + bdk_job_name
            def target_dir = target_root + '/' + "${env.BUILD_NUMBER}"
            def target_web = web_root + "/starfish/" + bdk_job_name + "/" + "${env.BUILD_NUMBER}"

            sh 'mkdir -p ' + target_root
            sh 'cp -r ' + org_dir + '/ ' + target_dir
            echo "Download Url: " + target_web
            currentBuild.description = target_web
        }
    }else {
        currentBuild.description = "FOSS: <a href=\"http://www.daum.net/\">No change</a>"
        echo "FOSS: <a href=\"http://www.daum.net/\">No change</a>"
        slackSend color: 'good', message: "${env.BUILD_URL} - No Change"
    }
}