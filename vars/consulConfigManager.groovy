// Fetch existing keys from Consul
def existingKeysResponse = sh(
    script: "curl -s ${consulAddr}/${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}?recurse=true",
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

def configKeys = jsonFile.keySet().collect { key ->
    "${env.ENV}/${env.CLUSTER}/${env.APPLICATION_CONFIG_MAP}/${key}"
}

// Remove keys from Consul that are not in config-map-env.json
def keysToRemove = existingKeys.findAll { !configKeys.contains(it) }
keysToRemove.each { key ->
    sh """
        curl -k -X DELETE '${consulAddr}/${key}'
    """
    echo "Deleted: ${key}"
}
