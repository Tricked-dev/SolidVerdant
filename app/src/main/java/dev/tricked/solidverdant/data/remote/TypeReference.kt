package dev.tricked.solidverdant.data.remote

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * A utility class for capturing generic type information.
 *
 * This is used to work around type erasure in Java/Kotlin, allowing generic types
 * to be correctly deserialized by libraries like `kotlinx.serialization`.
 */
abstract class TypeReference<T> : Comparable<TypeReference<T>> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]

    override fun compareTo(other: TypeReference<T>) = 0
}
