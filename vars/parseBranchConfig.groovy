def call(String branchName) {
    try {
        def cleanBranch = branchName.replace('origin/', '')
        def parts = cleanBranch.split(/\./)
        
        if(parts.size() != 3) {
            error "Invalid branch format. Expected: env.cluster.application-config-map"
        }
        
        env.ENV = parts[0]
        env.CLUSTER = parts[1]
        env.APPLICATION_CONFIG_MAP = parts[2]
        
        echo """✅ Successfully parsed branch:
        - ENV: ${env.ENV}
        - CLUSTER: ${env.CLUSTER}
        - APP_CONFIG: ${env.APPLICATION_CONFIG_MAP}"""
        
    } catch(Exception e) {
        error "Branch parsing failed: ${e.getMessage()}"
    }
}
