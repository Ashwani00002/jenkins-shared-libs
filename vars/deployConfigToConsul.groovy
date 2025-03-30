#!/usr/bin/groovy

def call(Map config) {
    def env = config.env
    def cluster = config.cluster
    def applicationConfigMap = config.applicationConfigMap
    def consulHttpAddr = config.consulHttpAddr

    node {
        stage('Install Consul Agent') {
            steps {
                script {
                    installConsulAgent()
                }
            }
        }

        stage('Process Config & Upload to Consul') {
            steps {
                script {
                    processConfigAndUpload(env, cluster, applicationConfigMap, consulHttpAddr)
                }
            }
        }
    }
}

def installConsulAgent() {
    def consulZip = 'consul.zip'
    def consulUrl = 'https://releases.hashicorp.com/consul/1.10.0/consul_1.10.0_linux_amd64.zip'

    sh "curl -sSL ${consulUrl} -o ${consulZip}"
    unzip zipFile: consulZip

    sh '''
        chmod +x consul && rm -rf consul.zip
        export PATH=$PWD:$PATH
        consul --version
    '''
}

def processConfigAndUpload(env, cluster, applicationConfigMap, consulHttpAddr) {
    try {
        def jFile = readJSON file: './config-map-env.json'

        println "JSON data: ${jFile}"

        if (jFile instanceof Map) {
            jFile.each { key, value ->
                def consulKey = "${env}/${cluster}/${applicationConfigMap}/${key}"
                echo "Consul Key: ${consulKey}, Value: ${value}"
                sh "curl -k --request PUT -d '${value}' '${consulHttpAddr}/${consulKey}'"
                sh "curl ${consulHttpAddr}/\\?recurse=true"

            }
        } else {
            error "Parsed JSON is not a Map (dictionary)."
        }
    } catch (Exception e) {
        error "Failed to process config-map-env.json: ${e.message}"
    }
}

def parseBranchName(branchName) {
    branchName = branchName.replace('origin/', '')
    def parts = branchName.split('\\.')
    if (parts.size() == 3) {
        return [env: parts[0], cluster: parts[1], applicationConfigMap: parts[2]]
    } else {
        error "Invalid branch name format. Expected: env.cluster.application-config-map"
    }
}