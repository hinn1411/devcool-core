# The only environment (ADR-0013). Both egress toggles are on while awake; hibernate (P2-T09)
# destroys app/edge first, then applies network with both set to false.
environment                = "dev"
enable_nat                 = true
enable_interface_endpoints = true
