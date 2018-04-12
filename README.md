# token-transfer-batch
token transfer batch

## Parameters of application as follow:

* web.gethHttpUrl: Your geth url eg. http://127.0.0.1:8545.
* web.tokenContractAddress: The address of the erc20 token.
* web.retryTimes: Retry times
* web.gas: Gas limit
* web.gasPrice: Gas price(wei)
* web.balanceFilePath: The file of the balance
* web.airDropContractAddress: The address of the batch contract
* web.walletFile: The file path of the wallet
* web.walletPassword: The password of the wallet
* web.approve: Whether or not call approve method
* web.startIndex: Start index
* web.length: Number of addresses called each time
* web.batch: Whether or not to send a transaction
* web.outputBalances: Whether or not to output balance