def call(String consulVersion = '1.10.0', String consulAddr = 'http://34.238.184.38:8500/v1/kv') {
    script {
        // Install Consul
        sh "curl -sSL https://releases.hashicorp.com/consul/${consulVersion}/consul_${consulVersion}_linux_amd64.zip -o consul.zip"
        unzip zipFile: 'consul.zip'
        sh 'chmod +x consul && rm consul.zip'
        
        // Process JSON config
        try {
            def jFile = readJSON file: './config-map-env.json'
            if(jFile instanceof Map) {
                jFile.each { key, value ->
                    def consulKey = "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
                    sh """
                        curl -k --request PUT -d '${value}' '${consulAddr}/${consulKey}'
                    """
                }
            } else {
                error "Invalid JSON structure in config-map-env.json"
            }
        } catch(Exception e) {
            error "Config processing failed: ${e.getMessage()}"
        }
    }
}