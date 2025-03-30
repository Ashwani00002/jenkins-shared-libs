def call(String branchName) {
    def parts = branchName.replace('origin/', '').split(/\./)
    if(parts.size() != 3) {
        error "Invalid branch format. Expected: env.cluster.application-config-map"
    }
    
    env.ENV = parts[0]
    env.CLUSTER = parts[1]
    env.APPLICATION_CONFIG_MAP = parts[2]
    echo "Parsed config: ENV=${env.ENV}, CLUSTER=${env.CLUSTER}, APPLICATION_CONFIG_MAP=${env.APPLICATION_CONFIG_MAP}"
}