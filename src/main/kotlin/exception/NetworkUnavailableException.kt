package exception

class NetworkUnavailableException(
    message: String = "Network is not available",
    cause: Throwable? = null
) : Exception(message, cause)
