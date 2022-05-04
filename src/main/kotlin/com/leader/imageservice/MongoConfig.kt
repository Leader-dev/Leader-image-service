package com.leader.imageservice

import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.connection.ClusterSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories
class MongoConfig : AbstractMongoClientConfiguration() {

    @Value("\${mongo.host}")
    private val host = ""

    @Value("\${mongo.port}")
    private val port = 0

    @Value("\${mongo.username}")
    private val username = ""

    @Value("\${mongo.authentication-database}")
    private val authenticationDatabase = ""

    @Value("\${mongo.password}")
    private val password = ""

    @Value("\${mongo.repository-database}")
    private val repositoryDatabase = ""

    override fun configureClientSettings(builder: MongoClientSettings.Builder) {
        builder.applyToClusterSettings { settings ->
            settings.hosts(
                listOf(ServerAddress(host, port))
            )
        }
        builder.credential(
            MongoCredential.createCredential(
                username,
                authenticationDatabase,
                password.toCharArray()
            )
        )
    }

    override fun getDatabaseName() = repositoryDatabase
}
