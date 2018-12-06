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

use clap::{App, Arg, ArgMatches, SubCommand};
use std::boxed::Box;
use std::error::Error;
use std::fmt;
use utils;
use web3::types::{Address, H256};
use web3::types;
use web3::contract::tokens::Tokenizable;
use ethabi::Token;
use types::{H192};

const CONTRACT_ADDRESS: &str = "contract_address";
const ETH_URL: &str = "eth_url";

#[derive(Debug)]
pub struct Status {
    version: u8,
    ready_nodes: u32,
    enqueued_codes: Vec<u32>,
}

impl Status {
    pub fn new(version: u8, ready_nodes: u32, enqueued_codes: Vec<u32>) -> Status {
        Status {
            version,
            ready_nodes,
            enqueued_codes,
        }
    }
}

impl fmt::Display for Status {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "Status: (\n\tversion: {},\n\tready nodes:{},\n\tenqueued codes lengths: {:?}\n)",
            self.version, self.ready_nodes, self.enqueued_codes
        )
    }
}

pub fn get_status_by_args(args: &ArgMatches) -> Result<(), Box<Error>> {
    let contract_address: Address = args
        .value_of(CONTRACT_ADDRESS)
        .unwrap()
        .trim_left_matches("0x")
        .parse()?;
    let eth_url: &str = args.value_of(ETH_URL).unwrap();

//    get_status(contract_address, eth_url)
    get_new_status(contract_address, eth_url)
}

pub fn get_status(contract_address: Address, eth_url: &str) -> Result<Status, Box<Error>> {
    let options = utils::options_with_gas(300_000);

    let (version, ready_nodes, enqueued_codes): (u64, u64, Vec<u64>) =
        utils::query_contract(contract_address, eth_url, "getStatus", (), options)?;

    Ok(Status::new(
        version as u8,
        ready_nodes as u32,
        enqueued_codes.into_iter().map(|x| x as u32).rev().collect(),
    ))
}

pub fn get_new_status(contract_address: Address, eth_url: &str) -> Result<(), Box<Error>> {
    let options = utils::options_with_gas(2300_000);

    println!("send status");

    let (clusters_indices, ready_nodes, solver_clusters): (Vec<Vec<u8>>, Vec<Vec<u8>>, Vec<Vec<u8>>) =
        utils::query_contract(contract_address, eth_url, "getStatus", (), options)?;

    let options2 = utils::options_with_gas(2300_000);

    let (nodes_indices, node_addresses, start_ports, end_ports, current_ports, solver_clusters_offsets): (Vec<H256>, Vec<H192>, Vec<u64>, Vec<u64>, Vec<u64>, Vec<u64>) =
        utils::query_contract(contract_address, eth_url, "getNodes", (), options2)?;

    println!("clusters indices: {:?}: ", clusters_indices);
    println!("ready_nodes: {:?}: ", ready_nodes);
    println!("solver_clusters: {:?}: ", solver_clusters);

    Ok(())
}

//Vec<[u8]>, Vec<[u8]>, Vec<[u8]>, Vec<[u8]>, Vec<[u8]>

pub fn subcommand<'a, 'b>() -> App<'a, 'b> {
    SubCommand::with_name("status")
        .about("Get status of smart contract")
        .args(&[
            Arg::with_name(CONTRACT_ADDRESS)
                .alias(CONTRACT_ADDRESS)
                .required(true)
                .takes_value(true)
                .help("deployer contract address"),
            Arg::with_name(ETH_URL)
                .alias(ETH_URL)
                .long("eth_url")
                .short("e")
                .required(false)
                .takes_value(true)
                .help("http address to ethereum node")
                .default_value("http://localhost:8545/"),
        ])
}
