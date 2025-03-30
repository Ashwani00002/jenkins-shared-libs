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
                // Extract just the key name (last part of the path)
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
            
            // Check if this is a renamed key (value matches an existing key's value)
            def oldKey = existingKeys.find { it.value.value == value }?.key
            
            if (oldKey && oldKey != key) {
                // This is a renamed key - delete the old one
                def oldFullPath = existingKeys[oldKey].fullPath
                sh """
                    curl -k -X DELETE '${consulAddr}/${oldFullPath}'
                """
                changes.deleted << oldKey
                existingKeys.remove(oldKey)  // Remove from tracking
                echo "Deleted renamed key: ${oldKey} (path: ${oldFullPath})"
            }
            
            // Check if we need to update/create the key
            if (!existingKeys.containsKey(key)) {
                // New key
                sh """
                    curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
                """
                changes.created << key
                existingKeys[key] = [value: value, fullPath: fullPath]  // Add to tracking
                echo "Created new key: ${key} (path: ${fullPath})"
            } else if (existingKeys[key].value != value) {
                // Updated key
                sh """
                    curl -k -X PUT -d '${value}' '${consulAddr}/${fullPath}'
                """
                changes.updated << key
                existingKeys[key] = [value: value, fullPath: fullPath]  // Update tracking
                echo "Updated key: ${key} (path: ${fullPath})"
            } else {
                echo "Skipped: ${key} (already exists with correct value)"
            }
        }

        // Identify keys to delete (present in Consul but not in config file)
        def keysToDelete = existingKeys.keySet().findAll { !jsonFile.containsKey(it) }
        keysToDelete.each { key ->
            def fullPath = existingKeys[key].fullPath
            sh """
                curl -k -X DELETE '${consulAddr}/${fullPath}'
            """
            changes.deleted << key
            echo "Deleted removed key: ${key} (path: ${fullPath})"
        }

        // Print summary of all changes
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