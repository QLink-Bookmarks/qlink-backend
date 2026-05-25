aws_region = "ap-northeast-2"

hosted_zone_name    = "qlinkapps.com"
hosted_zone_comment = "HostedZone created by Route53 Registrar"
hosted_zone_name_servers = [
  "ns-1423.awsdns-49.org.",
  "ns-480.awsdns-60.com.",
  "ns-598.awsdns-10.net.",
  "ns-1948.awsdns-51.co.uk."
]
hosted_zone_soa_value = "ns-1423.awsdns-49.org. awsdns-hostmaster.amazon.com. 1 7200 900 1209600 86400"

acm_certificate_arn    = "arn:aws:acm:ap-northeast-2:650177546654:certificate/aa14e1b6-aef4-4f74-bea6-2bff4afc8508"
acm_certificate_domain = "qlinkapps.com"

acm_validation_records = {
  "*.qlinkapps.com" = {
    name  = "_9f6d992ea3d5f6b0e77895dcf7a23b07.qlinkapps.com"
    type  = "CNAME"
    value = "_fcbcabff071778f5d110f67035e6c41c.jkddzztszm.acm-validations.aws."
  }
  "*.api.qlinkapps.com" = {
    name  = "_27380fc4985f296b2a2fda63e4cefcab.api.qlinkapps.com"
    type  = "CNAME"
    value = "_83daeec6d0cebe07511469dccdbeb39f.jkddzztszm.acm-validations.aws."
  }
}
