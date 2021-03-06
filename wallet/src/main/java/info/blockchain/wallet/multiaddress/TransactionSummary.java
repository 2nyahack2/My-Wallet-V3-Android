package info.blockchain.wallet.multiaddress;

import java.math.BigInteger;
import java.util.HashMap;

public class TransactionSummary {

    public enum TransactionType {
        TRANSFERRED, RECEIVED, SENT, BUY, SELL, SWAP, DEPOSIT, WITHDRAW, INTEREST_EARNED, UNKNOWN
    }

    private String hash;
    private BigInteger total;//Total actually sent, including fee
    private BigInteger fee;//Total fee used

    private TransactionType transactionType;
    private long time;
    private int confirmations;
    private boolean isDoubleSpend;
    private boolean isPending;//Sent to server but not confirmed

    //Address - Amount map
    HashMap<String, BigInteger> inputsMap = new HashMap<>();
    HashMap<String, BigInteger> outputsMap = new HashMap<>();

    //Address - xpub map (Fastest way to convert address to xpub)
    HashMap<String, String> inputsXpubMap = new HashMap<>();
    HashMap<String, String> outputsXpubMap = new HashMap<>();

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(int confirmations) {
        this.confirmations = confirmations;
    }

    public BigInteger getTotal() {
        return total;
    }

    public void setTotal(BigInteger total) {
        this.total = total;
    }

    public BigInteger getFee() {
        return fee;
    }

    public void setFee(BigInteger fee) {
        this.fee = fee;
    }

    public HashMap<String, BigInteger> getInputsMap() {
        return inputsMap;
    }

    public void setInputsMap(HashMap<String, BigInteger> inputsMap) {
        this.inputsMap = inputsMap;
    }

    public HashMap<String, BigInteger> getOutputsMap() {
        return outputsMap;
    }

    public void setOutputsMap(HashMap<String, BigInteger> outputsMap) {
        this.outputsMap = outputsMap;
    }

    public boolean isDoubleSpend() {
        return isDoubleSpend;
    }

    public void setDoubleSpend(boolean doubleSpend) {
        isDoubleSpend = doubleSpend;
    }

    public HashMap<String, String> getInputsXpubMap() {
        return inputsXpubMap;
    }

    public void setInputsXpubMap(HashMap<String, String> inputsXpubMap) {
        this.inputsXpubMap = inputsXpubMap;
    }

    public HashMap<String, String> getOutputsXpubMap() {
        return outputsXpubMap;
    }

    public void setOutputsXpubMap(HashMap<String, String> outputsXpubMap) {
        this.outputsXpubMap = outputsXpubMap;
    }

    public boolean isPending() {
        return isPending;
    }

    public void setPending(boolean pending) {
        isPending = pending;
    }
}
