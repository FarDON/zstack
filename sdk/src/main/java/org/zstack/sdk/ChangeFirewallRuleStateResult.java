package org.zstack.sdk;

import org.zstack.sdk.VpcFirewallRuleInventory;

public class ChangeFirewallRuleStateResult {
    public VpcFirewallRuleInventory inventory;
    public void setInventory(VpcFirewallRuleInventory inventory) {
        this.inventory = inventory;
    }
    public VpcFirewallRuleInventory getInventory() {
        return this.inventory;
    }

}
