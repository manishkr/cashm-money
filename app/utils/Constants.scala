package utils

object Constants {

}

object CurrencyCode extends Enumeration {
  type CurrencyCode = Value
  val INR: utils.CurrencyCode.Value = Value("INR")
}

object TransferStatus extends Enumeration {
  type TransferStatus = Value
  val Success: TransferStatus.Value = Value("Transfer successful")
  val InsufficentFund: TransferStatus.Value = Value("Transfer failed due to insufficient fund")
  val SameAccount: TransferStatus.Value = Value("Transfer failed due to same account")
  val NegtaitveAmount: TransferStatus.Value = Value("Transfer amount is negative")
  val Failed: TransferStatus.Value = Value("Transfer failed")
}