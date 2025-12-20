package org.roland.scala3_zio_template.config

import zio.{Config, _}

/** Server configuration.
  *
  * @param port
  *   HTTP server port (default: 8080)
  */
case class ServerConfig(port: Int)

object ServerConfig:
  /** ZLayer that loads ServerConfig from environment variables.
    *
    * Environment variables:
    *   - SERVER_PORT: HTTP port (default: 8080)
    */
  val layer: ZLayer[Any, Config.Error, ServerConfig] =
    ZLayer.fromZIO(
      ZIO.config[ServerConfig](
        Config
          .int("SERVER_PORT")
          .withDefault(8080)
          .map(port => ServerConfig(port))
      )
    )

/** Database configuration.
  *
  * @param host
  *   PostgreSQL server hostname (default: localhost)
  * @param port
  *   PostgreSQL server port (default: 5432)
  * @param name
  *   Database name (default: postgres)
  * @param user
  *   Database username (required)
  * @param password
  *   Database password (required)
  */
case class DatabaseConfig(
    host: String,
    port: Int,
    name: String,
    user: String,
    password: String
):
  /** Constructs a JDBC URL from the configuration parameters.
    *
    * @return
    *   JDBC connection string in the format:
    *   jdbc:postgresql://host:port/database
    */
  def jdbcUrl: String =
    s"jdbc:postgresql://$host:$port/$name"

object DatabaseConfig:
  /** ZLayer that loads DatabaseConfig from environment variables.
    *
    * Environment variables:
    *   - DATABASE_HOST: PostgreSQL hostname (default: localhost)
    *   - DATABASE_PORT: PostgreSQL port (default: 5432)
    *   - DATABASE_NAME: Database name (default: postgres)
    *   - DATABASE_USER: Database username (required)
    *   - DATABASE_PASSWORD: Database password (required)
    */
  val layer: ZLayer[Any, Config.Error, DatabaseConfig] =
    ZLayer.fromZIO(
      ZIO.config[DatabaseConfig](
        (
          Config.string("DATABASE_HOST").withDefault("localhost") ++
            Config.int("DATABASE_PORT").withDefault(5432) ++
            Config.string("DATABASE_NAME").withDefault("postgres") ++
            Config.string("DATABASE_USER") ++
            Config.string("DATABASE_PASSWORD")
        ).map { case (host, port, name, user, password) =>
          DatabaseConfig(host, port, name, user, password)
        }
      )
    )

/** AWS configuration.
  *
  * @param accessKeyId
  *   AWS access key ID (required)
  * @param secretAccessKey
  *   AWS secret access key (required)
  * @param region
  *   AWS region (default: us-east-1)
  */
case class AwsConfig(
    accessKeyId: String,
    secretAccessKey: String,
    region: String
)

object AwsConfig:
  /** ZLayer that loads AwsConfig from environment variables.
    *
    * Environment variables:
    *   - AWS_ACCESS_KEY_ID: AWS access key (required)
    *   - AWS_SECRET_ACCESS_KEY: AWS secret key (required)
    *   - AWS_REGION: AWS region (default: us-east-1)
    */
  val layer: ZLayer[Any, Config.Error, AwsConfig] =
    ZLayer.fromZIO(
      ZIO.config[AwsConfig](
        (
          Config.string("AWS_ACCESS_KEY_ID") ++
            Config.string("AWS_SECRET_ACCESS_KEY") ++
            Config.string("AWS_REGION").withDefault("us-east-1")
        ).map { case (accessKeyId, secretAccessKey, region) =>
          AwsConfig(accessKeyId, secretAccessKey, region)
        }
      )
    )

/** Complete application configuration.
  *
  * @param server
  *   Server configuration
  * @param database
  *   Database configuration
  * @param aws
  *   AWS configuration
  */
case class AppConfig(
    server: ServerConfig,
    database: DatabaseConfig,
    aws: AwsConfig
)

object AppConfig:
  /** ZLayer that loads complete AppConfig from environment variables.
    *
    * This layer combines all three sub-configurations and will fail fast at
    * application startup if any required configuration is missing.
    */
  val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.make[AppConfig](
      ServerConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      DatabaseConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      AwsConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      ZLayer.fromZIO(
        for {
          server <- ZIO.service[ServerConfig]
          database <- ZIO.service[DatabaseConfig]
          aws <- ZIO.service[AwsConfig]
        } yield AppConfig(server, database, aws)
      )
    )
