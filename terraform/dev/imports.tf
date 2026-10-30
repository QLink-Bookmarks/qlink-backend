# Import existing default routes when moving from inline route blocks to aws_route.
import {
  to = module.network.aws_route.public_default
  id = "${module.network.public_route_table_id}_0.0.0.0/0"
}

import {
  to = module.shared_network.aws_route.public_default
  id = "${module.shared_network.public_route_table_id}_0.0.0.0/0"
}
