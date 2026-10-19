# The only environment (ADR-0013). Both egress toggles are on while awake; hibernate (P2-T09)
# destroys app, applies data with enable_valkey=false, then network with both set to false.
# edge is never touched (ADR-0013).
environment                = "dev"
enable_nat                 = true
enable_interface_endpoints = true
