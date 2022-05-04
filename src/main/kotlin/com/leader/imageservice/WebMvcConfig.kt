package com.leader.imageservice

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.leader.imageservice.util.InternalErrorException
import com.leader.imageservice.util.UserAuthException
import com.leader.imageservice.util.authError
import com.leader.imageservice.util.internalError
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
@EnableWebMvc
@ControllerAdvice
class WebMvcConfig @Autowired constructor(
    private val threadJWTData: ThreadJWTData
) : WebMvcConfigurer {

    companion object {
        const val TOKEN_HEADER_KEY = "API-Token"
        const val SET_TOKEN_HEADER_KEY = "Set-API-Token"
        const val DATA_KEY = "data"
    }

    @Value("\${leader.jwt-secret}")
    private val jwtSecret: String = ""

    private val jwtAlgorithm: Algorithm
        get() = Algorithm.HMAC256(jwtSecret)

    override fun addCorsMappings(registry: CorsRegistry) {
        // allow CORS for all paths
        registry
            .addMapping("/**")
            .allowedMethods("GET", "POST", "OPTIONS")
            .exposedHeaders("Set-API-Token")
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        // add base authentication checker for all routes other than /user/** and /admin/**
        registry
            .addInterceptor(object : HandlerInterceptor {
                override fun preHandle(
                    request: HttpServletRequest,
                    response: HttpServletResponse,
                    handler: Any
                ): Boolean {
                    val verifier = JWT.require(jwtAlgorithm).build()
                    val data = try {
                        val jwt = verifier.verify(request.getHeader(TOKEN_HEADER_KEY))
                        jwt.claims[DATA_KEY]!!.asMap().toMutableMap()
                    } catch (_: Exception) {
                        createJWTData()
                    }
                    threadJWTData.loadCurrentData(data) { newData ->
                        val jwt = JWT.create().withClaim(DATA_KEY, newData)
                        response.addHeader(SET_TOKEN_HEADER_KEY, jwt.sign(jwtAlgorithm))
                    }
                    return true
                }
                override fun postHandle(
                    request: HttpServletRequest,
                    response: HttpServletResponse,
                    handler: Any,
                    modelAndView: ModelAndView?
                ) {
                    threadJWTData.removeCurrentData()
                }
            })
            .addPathPatterns("/**")
    }

    @ExceptionHandler(Exception::class)
    fun handle(ex: Exception): ResponseEntity<Document> {
        // Special handling for user auth failed, return auth error (403)
        if (ex is UserAuthException) {
            return ResponseEntity(authError(), HttpStatus.OK)
        }
        // if an exception other than defined types occur, print trace in console for debug
        if (ex !is InternalErrorException || ex.cause != null) {
            ex.printStackTrace()
        }
        // whenever an exception occur, return internal error (500) along with the original message of the exception
        return ResponseEntity(internalError(ex.message), HttpStatus.OK)
    }

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>?>) {
        // whenever a ObjectId object is encountered in response, convert it to hex string (toString() function does this)
        val builder = Jackson2ObjectMapperBuilder()
            .serializerByType(ObjectId::class.java, ToStringSerializer())
        val converter = MappingJackson2HttpMessageConverter(builder.build())
        converters.add(converter)
    }
}

typealias JWTData = MutableMap<String, Any?>
fun createJWTData(): JWTData = mutableMapOf()

@Component
class ThreadJWTData {
    private val dataThreadLocal = ThreadLocal<JWTData>()
    private var currentData: JWTData
        get() = dataThreadLocal.get()
        set(data) { dataThreadLocal.set(data) }

    private var onModifiedThreadLocal = ThreadLocal<(JWTData) -> Unit>()
    private var onModified: (JWTData) -> Unit
        get() = onModifiedThreadLocal.get()
        set(modified) { onModifiedThreadLocal.set(modified) }

    fun loadCurrentData(data: JWTData, onModified: (JWTData) -> Unit = {}) {
        currentData = data
        this.onModified = onModified
    }

    fun removeCurrentData() {
        dataThreadLocal.remove()
    }

    operator fun get(key: String): Any? = currentData[key]

    operator fun set(key: String, value: Any?) {
        currentData[key] = value
        onModifiedThreadLocal.get().invoke(currentData)
    }

    fun remove(key: String) {
        currentData.remove(key)
        onModifiedThreadLocal.get().invoke(currentData)
    }
}