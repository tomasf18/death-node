package com.deathnode.monitor;

import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;

import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;

public class Sniffer implements Runnable {

    private final String interfaceName;
    private final Aggregator aggregator;

    public Sniffer(String interfaceName, Aggregator aggregator) {
        this.interfaceName = interfaceName;
        this.aggregator = aggregator;
    }

    @Override
    public void run() {
        try {
            sniff();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sniff() throws PcapNativeException, NotOpenException, InterruptedException {

        PcapNetworkInterface nif = Pcaps.getDevByName(interfaceName);
        if (nif == null) {
            throw new IllegalStateException(
                    "Interface de rede não encontrada: " + interfaceName
            );
        }

        PcapHandle handle = nif.openLive(
                65536, // snaplen
                PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                10     // timeout ms
        );

        handle.setFilter(
                "tcp",
                BpfProgram.BpfCompileMode.OPTIMIZE
        );

        PacketListener listener = this::processPacket;

        System.out.println(
                "[com.deathnode.monitor.Sniffer] Listening on " + interfaceName
        );

        handle.loop(-1, listener);
    }

    private void processPacket(Packet packet) {

        IpV4Packet ipPacket = packet.get(IpV4Packet.class);
        TcpPacket tcpPacket = packet.get(TcpPacket.class);

        if (ipPacket == null || tcpPacket == null) {
            return;
        }

        String srcIp =
                ipPacket.getHeader()
                        .getSrcAddr()
                        .getHostAddress();

        String dstIp =
                ipPacket.getHeader()
                        .getDstAddr()
                        .getHostAddress();

        int packetSize = packet.length();

        aggregator.record(srcIp, packetSize);
    }
}
