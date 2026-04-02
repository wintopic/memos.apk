package com.usememos.android

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

private const val GO_BRIDGE_CLASS = "com.usememos.mobile.Memosmobile"

object GoBackend {
  fun isInstalled(): Boolean = loadBridgeClass() != null

  fun version(): String = invokeString(emptyArray<Any>(), "version", "Version")

  @Throws(Exception::class)
  fun start(dataDir: String, port: Int): String = invokeString(arrayOf(dataDir, port), "start", "Start")

  fun stop() {
    invokeVoid(emptyArray<Any>(), "stop", "Stop")
  }

  private fun invokeString(arguments: Array<Any>, vararg methodNames: String): String {
    val result = invoke(arguments, *methodNames)
    return result as? String ?: ""
  }

  private fun invokeVoid(arguments: Array<Any>, vararg methodNames: String) {
    invoke(arguments, *methodNames)
  }

  private fun invoke(arguments: Array<Any>, vararg methodNames: String): Any? {
    val bridgeClass = loadBridgeClass() ?: throw IllegalStateException(
      "Go bindings are missing. Run scripts/android/build-android-bindings.sh or build-android-bindings.ps1 first.",
    )
    val method = findMethod(bridgeClass, arguments.size, methodNames.toSet())
      ?: throw IllegalStateException("Unable to find ${methodNames.joinToString("/")} on $GO_BRIDGE_CLASS.")
    val preparedArguments = adaptArguments(method, arguments)

    return try {
      method.invoke(null, *preparedArguments)
    } catch (exception: InvocationTargetException) {
      val cause = exception.targetException ?: exception
      when (cause) {
        is Exception -> throw cause
        else -> throw IllegalStateException(cause.message ?: "Go bridge invocation failed.", cause)
      }
    }
  }

  private fun loadBridgeClass(): Class<*>? = try {
    Class.forName(GO_BRIDGE_CLASS)
  } catch (_: ClassNotFoundException) {
    null
  }

  private fun findMethod(bridgeClass: Class<*>, parameterCount: Int, names: Set<String>): Method? =
    bridgeClass.methods.firstOrNull { method ->
      method.name in names && method.parameterTypes.size == parameterCount
    }

  private fun adaptArguments(method: Method, arguments: Array<Any>): Array<Any> {
    val parameterTypes = method.parameterTypes
    return Array(arguments.size) { index ->
      val argument = arguments[index]
      when (parameterTypes[index]) {
        java.lang.Integer.TYPE, java.lang.Integer::class.java -> (argument as Number).toInt()
        java.lang.Long.TYPE, java.lang.Long::class.java -> (argument as Number).toLong()
        else -> argument
      }
    }
  }
}
