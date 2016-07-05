/*
parallel node('verification'){
        build job: 'z-build1'
    }, node('verification2'){
            build job: 'z-build2'
        }
*/
//build job: 'test_second_job', parameters: [[$class: 'StringParameterValue', name: 'check1', value: '123'], [$class: 'StringParameterValue', name: 'check2', value: '456']]

stage 'build1'
join = parallel([first: {
        node('verification'){
            build job:'z-build1'
        }
    }, second: {
        node('verification2'){
            build job:'z-build2'
        }
    }
])

stage 'build2'
build(job:'test_second_job', 
    parameters:[
        [$class: 'StringParameterValue', name:'check1', value:join.first.result], 
        [$class: 'StringParameterValue', name:'check2', value:join.first.getDuration()]
    ]
)
