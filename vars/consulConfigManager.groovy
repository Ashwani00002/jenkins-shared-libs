// vars/consulConfigManager.groovy
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

        // Fetch existing keys and values from Consul
        def existingKeysResponse = sh(
            script: "curl -s ${consulAddr}/${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}?recurse=true",
            returnStdout: true
        ).trim()

        def existingKeys = [:]  // Map to store existing keys and their values
        if (existingKeysResponse) {
            def parsedResponse = readJSON text: existingKeysResponse
            parsedResponse.each { item ->
                existingKeys[item.Key] = item.Value
            }
        }

        // Read keys and values from config-map-env.json
        def jsonFile = readJSON file: './config-map-env.json'

        if (!(jsonFile instanceof Map)) {
            error "Invalid JSON structure: Expected object at root level"
        }

        // Construct full paths for keys in config-map-env.json
        def configKeys = jsonFile.collectEntries { key, value ->
            ["${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}": value]
        }

        // Add or update keys in Consul only if necessary
        configKeys.each { fullPath, value ->
            if (!existingKeys.containsKey(fullPath) || existingKeys[fullPath] != value) {
                sh """
                    curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
                """
                echo "Uploaded/Updated: ${fullPath} with value: ${value}"
            } else {
                echo "Skipped: ${fullPath} (already exists with correct value)"
            }
        }

        // Remove keys from Consul that are not in config-map-env.json
        def keysToRemove = existingKeys.keySet().findAll { !configKeys.containsKey(it) }
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
