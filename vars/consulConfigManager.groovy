def call(Map config = [:]) {
    def consulVersion = config.consulVersion ?: '1.10.0'
    def consulAddr = config.consulAddr ?: 'http://34.238.184.38:8500/v1/kv'

    try {
        // Install Consul
        sh """
            curl -sSL https://releases.hashicorp.com/consul/${consulVersion}/consul_${consulVersion}_linux_amd64.zip -o consul.zip
        """
        unzip zipFile: 'consul.zip'
        sh "chmod +x consul && rm -f consul.zip"

        // Fetch existing keys from Consul
        def existingKeysResponse = sh(
            script: "curl -s ${consulAddr}/?recurse=true",
            returnStdout: true
        ).trim()
        
        def existingKeys = []
        if (existingKeysResponse) {
            def parsedResponse = readJSON text: existingKeysResponse
            existingKeys = parsedResponse.collect { it.Key }
        }

        // Read keys from config-map-env.json
        def jsonFile = readJSON file: './config-map-env.json'
        
        if (!(jsonFile instanceof Map)) {
            error "Invalid JSON structure: Expected object at root level"
        }

        def newKeys = jsonFile.keySet().collect { key ->
            "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
        }

        // Add or update keys in Consul
        jsonFile.each { key, value ->
            def fullPath = "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
            sh """
                curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
            """
            echo "Uploaded: ${fullPath}"
        }

        // Remove keys from Consul that are not in config-map-env.json
        def keysToRemove = existingKeys.findAll { !newKeys.contains(it) }
        keysToRemove.each { key ->
            sh """
                curl -k -X DELETE '${consulAddr}/${key}'
            """
            echo "Deleted: ${key}"
        }

    } catch (Exception e) {
        error "Consul configuration failed: ${e.getMessage()}"
    } finally {
        sh "rm -f consul* || true"  // Cleanup
    }
}
