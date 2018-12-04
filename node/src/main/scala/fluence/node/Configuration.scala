/*
 * Copyright 2018 Fluence Labs Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fluence.node
import java.nio.file.{Files, Path, Paths}

import cats.effect.{ContextShift, IO}
import fluence.node.config.{MasterConfig, NodeConfig, StatServerConfig, SwarmConfig}
import fluence.node.eth.DeployerContractConfig
import fluence.node.tendermint.KeysPath
import ConfigOps._
import pureconfig.generic.auto._

case class Configuration(
  rootPath: Path,
  nodeConfig: NodeConfig,
  contractConfig: DeployerContractConfig,
  swarm: Option[SwarmConfig],
  statistics: StatServerConfig,
  ethereumRPC: EthereumRPCConfig,
  masterContainerId: String
)

object Configuration {

  /**
    * Load config at /master/application.conf with fallback on config from class loader
    */
  def loadConfig(): IO[Config] = {
    import ConfigFactoryWrapper._
    val containerConfig = "/master/application.conf"

    (loadFile(Paths.get(containerConfig)) match {
      case Left(_) => load() // exception will be printed out later, see ConfigOps
      case Right(config) => load.map(config.withFallback)
    }).toIO
  }

  def create()(implicit ec: ContextShift[IO]): IO[(MasterConfig, Configuration)] = {
    for {
      masterConfig <- pureconfig.loadConfig[MasterConfig].toIO
      rootPath <- IO(Paths.get(masterConfig.tendermintPath).toAbsolutePath)
      t <- tendermintInit(masterNodeContainerId, rootPath, solverImage)
      (nodeId, validatorKey) = t
      solverInfo <- NodeConfig(masterKeys, masterConfig.endpoints)
    } yield
      (
        masterConfig,
        Configuration(
          rootPath,
          masterKeys,
          solverInfo,
          masterConfig.deployer,
          masterConfig.swarm,
          masterConfig.statServer
        )
      )
  }

  /**
    * Run `tendermint --init` in container to initialize /master/tendermint/config with configuration files.
    * Later, files /master/tendermint/config are used to run and configure solvers
    * @param masterContainer id of master docker container (container running this code)
    * @return nodeId and validator key
    */
  def tendermintInit(masterContainer: String, rootPath: Path, solverImage: SolverImage)(
    implicit c: ContextShift[IO]
  ): IO[(String, ValidatorKey)] = {

    val tendermintDir = rootPath.resolve("tendermint") // /master/tendermint
    def tendermint(cmd: String, uid: String) = {
      DockerParams
        .run("tendermint", cmd, s"--home=$tendermintDir")
        .user(uid)
        .option("--volumes-from", masterContainer)
        .image(solverImage.imageName)
    }

    for {
      uid <- IO(scala.sys.process.Process("id -u").!!.trim)
      //TODO: don't do tendermint init if keys already exist
      _ <- DockerIO.run[IO](tendermint("init", uid)).compile.drain

      _ <- IO {
        tendermintDir.resolve("config").resolve("config.toml").toFile.delete()
        tendermintDir.resolve("config").resolve("genesis.json").toFile.delete()
        tendermintDir.resolve("data").toFile.delete()
      }

      nodeId <- DockerIO.run[IO](tendermint("show_node_id", uid)).compile.lastOrError

      validatorRaw <- DockerIO.run[IO](tendermint("show_validator", uid)).compile.lastOrError
      validator <- IO.fromEither(parse(validatorRaw).flatMap(_.as[ValidatorKey]))
    } yield (nodeId, validator)
  }
}
