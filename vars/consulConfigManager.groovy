def call(Map config = [:]) {
    def consulVersion = config.consulVersion ?: '1.10.0'
    def consulAddr = config.consulAddr ?: 'http://34.238.184.38:8500/v1/kv'
    
    try {
        // Install Consul
        sh """
            curl -sSL https://releases.hashicorp.com/consul/${consulVersion}/consul_${consulVersion}_linux_amd64.zip -o consul.zip
            unzip -o consul.zip
            chmod +x consul
            rm -f consul.zip
        """
        
        // Process Configuration
        def jsonFile = readJSON file: './config-map-env.json'
        
        if(!(jsonFile instanceof Map)) {
            error "Invalid JSON structure: Expected object at root level"
        }
        
        jsonFile.each { key, value ->
            def fullPath = "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
            sh """
                curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
            """
            echo "Uploaded: ${fullPath}"
        }
        
    } catch(Exception e) {
        error "Consul configuration failed: ${e.getMessage()}"
    } finally {
        sh "rm -f consul* || true"  # Cleanup
    }
}
