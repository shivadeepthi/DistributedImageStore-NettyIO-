package poke.server.storage;

public class ReplicaDomain {
	
	private int primaryNode;
	private int secondaryNode1;
	private int secondaryNode2;
	
	public int getPrimaryNode() {
		return primaryNode;
	}
	public void setPrimaryNode(int primaryNode) {
		this.primaryNode = primaryNode;
	}
	public int getSecondaryNode1() {
		return secondaryNode1;
	}
	public void setSecondaryNode1(int secondaryNode1) {
		this.secondaryNode1 = secondaryNode1;
	}
	public int getSecondaryNode2() {
		return secondaryNode2;
	}
	public void setSecondaryNode2(int secondaryNode2) {
		this.secondaryNode2 = secondaryNode2;
	}
	
}
