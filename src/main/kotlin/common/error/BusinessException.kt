package com.qlink.common.error

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
