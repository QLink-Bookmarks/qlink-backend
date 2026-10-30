# Import the existing default route when moving from an inline route block.
import {
  to = module.network.aws_route.public_default
  id = "${module.network.public_route_table_id}_0.0.0.0/0"
}
