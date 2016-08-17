node('master_pipeline') {
   stage 'Parse build job info'
   def download_prefix = "starfish/"
   def web_root = "http://webos-ci.lge.com/download/"
   def local_root = "/binary/build_results/"
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
   git branch: 'master', url: 'http://mod.lge.com/hub/tv_scm_tool/compare_foss_diff.git'

   stage 'Check foss change'
   sh "python compare_foss_diff.py --jobname ${BUILD_JOB_NAME} --buildnumber ${BUILD_JOB_NUMBER} > compare_result"
   sh "cat compare_result"
   def compare_result = readFile 'compare_result'
   if ( compare_result == "CHANGED\n" ) {
       echo "FOSS: " + compare_result
       stage 'Call starfishbdk-official-all'
       echo 'Clean Build'
       def target_job_name = "starfishbdk-official-all"
       def build_machines = machine_name
       def official_build_url = "${env.JENKINS_URL}".toString() + "job/" + job_name + "/" + build_number + "/";
       build_machines += " qemux86"
       currentBuild.description = "From        :<a href=\"" + official_build_url + "\">" + job_name + ":" + build_number + "</a>";
       join = parallel([bdk_build: {
                build job:target_job_name, parameters: [
                    [$class: 'StringParameterValue',  name:'SDK_BUILD_BRANCH',        value:"@" + branch_name],
                    [$class: 'StringParameterValue',  name:'SDK_BUILD_NUMBER',        value:build_number],
                    [$class: 'StringParameterValue',  name:'BUILD_PLATFORM_CODENAME', value:'dreadlocks'],
                    [$class: 'StringParameterValue',  name:'BUILD_SDKMACHINES',       value:'i686'],
                    [$class: 'StringParameterValue',  name:'BUILD_MACHINES',          value:build_machines],
                    [$class: 'StringParameterValue',  name:'BUILD_CLEANUP_TYPE',      value:'clean'],
                    [$class: 'StringParameterValue',  name:'token',                   value:'trigger_bdk_build'],
                ]
        }
        ])

        def bdk_build_result = join.bdk_build.result
        def bdk_build_number = join.bdk_build.number.toString()

        if (bdk_build_result == "SUCCESS" ) {
            stage 'Set description';
            def target_web = web_root +  download_prefix + target_job_name + '/' + bdk_build_number;
            def target_local = local_root +  download_prefix + target_job_name + '/' + bdk_build_number;
            def target_job_url = "${env.JENKINS_URL}".toString() + "job/" + target_job_name + "/" + bdk_build_number + "/";
            currentBuild.description += '<br/>BDK download: <a href=\"' + target_web + '\">' + target_job_name + ':' + bdk_build_number+ '</a>';
            currentBuild.description += '<br/>BDK buildjob: <a href=\"' + target_job_url + '\">' + 'Build job link</a>';

            stage 'Upload bdk files';
            sh "rm -rf bms-uploader";
            sh "wget -O bms-uploader http://mod.lge.com/code/projects/WBM/repos/bms-uploader/browse/bms-uploader?raw"
            sh "chmod +x bms-uploader";
            def workspace = pwd();
            dir(target_local){
                def bdk_files = findFiles glob: '*/*.sh';
                echo "Check";
                def no_of_files = bdk_files.length;
                for (int i = 0; i< no_of_files; i++) {
                    def full_path  = target_local + "/" + bdk_files[i].path;
                    def file_name = bdk_files[i].name;
                    file_version = file_name.replaceAll('starfish-bdk-i686-', '');
                    file_version = file_version.substring(0, file_version.length() - 3);
                    sh workspace.toString() + "/bms-uploader ".toString() + full_path.toString() + " --username gatekeeper.tvsw --password LasVegas1! -m starfish-bdk -p webos-pro -c i686 -v ".toString() + file_version.toString() + " -o -q --use_original".toString();
                    echo "INFO: Sleep for 5 seconds";
                    sleep(5);
                }
            }
        }
    }else {
        currentBuild.description = "No change"
        currentBuild.description += '<br/>From ' + job_name + ':' + build_number
        slackSend color: 'good', message: "${env.BUILD_URL} - No Change"
    }
}
