def call(Map config = [:]) {
    def consulAddr = env.CONSUL_HTTP_ADDR  // Use environment variable directly

    if (!consulAddr) {
        error "CONSUL_HTTP_ADDR is not defined in the pipeline environment."
    }

    try {
        // Install Consul
        sh """
            curl -sSL https://releases.hashicorp.com/consul/${config.consulVersion ?: '1.10.0'}/consul_${config.consulVersion ?: '1.10.0'}_linux_amd64.zip -o consul.zip
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
                def keyName = item.Key.tokenize('/').last()
                existingKeys[keyName] = [
                    value: item.Value,
                    fullPath: item.Key
                ]
            }
        }

        // Read keys and values from config-map-env.json
        def jsonFile = readJSON file: './config-map-env.json'

        if (!(jsonFile instanceof Map)) {
            error "Invalid JSON structure: Expected object at root level"
        }

        // Initialize change tracking
        def changes = [
            created: [],
            updated: [],
            deleted: []
        ]

        // Process changes - update or add new keys
        jsonFile.each { key, value ->
            def fullPath = "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
            
            def oldKey = existingKeys.find { it.value.value == value }?.key
            
            if (oldKey && oldKey != key) {
                def oldFullPath = existingKeys[oldKey].fullPath
                sh """
                    curl -k -X DELETE '${consulAddr}/${oldFullPath}'
                """
                changes.deleted << oldKey
                existingKeys.remove(oldKey)
                echo "Deleted renamed key: ${oldKey} (path: ${oldFullPath})"
            }
            
            if (!existingKeys.containsKey(key)) {
                sh """
                    curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
                """
                changes.created << key
                existingKeys[key] = [value: value, fullPath: fullPath]
                echo "Created new key: ${key} (path: ${fullPath})"
            } else if (existingKeys[key].value != value) {
                sh """
                    curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
                """
                changes.updated << key
                existingKeys[key] = [value: value, fullPath: fullPath]
                echo "Updated key: ${key} (path: ${fullPath})"
            } else {
                echo "Skipped: ${key} (already exists with correct value)"
            }
        }

        def keysToDelete = existingKeys.keySet().findAll { !jsonFile.containsKey(it) }
        keysToDelete.each { key ->
            def fullPath = existingKeys[key].fullPath
            sh """
                curl -k -X DELETE '${consulAddr}/${fullPath}'
            """
            changes.deleted << key
            echo "Deleted removed key: ${key} (path: ${fullPath})"
        }

        echo "\n=== CONSUL CONFIGURATION CHANGES SUMMARY ==="
        if (changes.created) {
            echo "Created keys (${changes.created.size()}):"
            changes.created.each { key -> echo "- ${key}" }
        } else {
            echo "No keys were created"
        }
        
        if (changes.updated) {
            echo "\nUpdated keys (${changes.updated.size()}):"
            changes.updated.each { key -> echo "- ${key}" }
        } else {
            echo "\nNo keys were updated"
        }
        
        if (changes.deleted) {
            echo "\nDeleted keys (${changes.deleted.size()}):"
            changes.deleted.each { key -> echo "- ${key}" }
        } else {
            echo "\nNo keys were deleted"
        }
        echo "==========================================="

    } catch (Exception e) {
        error "Consul configuration failed: ${e.getMessage()}"
    } finally {
        sh "rm -f consul* || true"  // Cleanup
    }
}
