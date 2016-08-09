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
       def target_job_name = "starfishbdk-official-all"
       def build_machines = machine_name
       currentBuild.description = currentBuild.description + "<br/>" + "Job name: " + job_name
       currentBuild.description = currentBuild.description + "<br/>" + "Build number: " + build_number 
       join = parallel([clean: {
            node('z-swfarm-gateuobld24'){
                build job:target_job_name, parameters: [
                    [$class: 'StringParameterValue',  name:'SDK_BUILD_BRANCH',        value:"@" + branch_name],
                    [$class: 'StringParameterValue',  name:'SDK_BUILD_NUMBER',        value:build_number],
                    [$class: 'StringParameterValue',  name:'BUILD_PLATFORM_CODENAME', value:'dreadlocks'],
                    [$class: 'StringParameterValue',  name:'BUILD_SDKMACHINES',       value:'i686'],
                    [$class: 'StringParameterValue',  name:'BUILD_MACHINES',          value:machine_name],
                    [$class: 'StringParameterValue',  name:'BUILD_CLEANUP_TYPE',      value:'clean'],
                    [$class: 'StringParameterValue',  name:'token',                   value:'trigger_bdk_build'],
                ]
            }
        }
        ])

        def clean_build_result = join.clean.result
        def clean_build_number = join.clean.number.toString()

        stage 'Copy bdk result'
        node('verification'){
            def bdk_job_name = "starfish-bdk"
            def org_dir = '/binary/build_results/starfish/' + target_job_name + '/' + clean_build_number
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
