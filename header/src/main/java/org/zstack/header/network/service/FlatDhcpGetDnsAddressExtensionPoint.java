package org.zstack.header.network.service;

import org.zstack.header.network.l3.L3NetworkInventory;

import java.util.List;

public interface FlatDhcpGetDnsAddressExtensionPoint {
    List<String> getDnsAddress(L3NetworkInventory inv);
}
