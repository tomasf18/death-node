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

import java.util.List;
import java.util.Scanner;

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
            List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
            for (PcapNetworkInterface dev : allDevs) {
                System.out.println(dev.getName() + " -> " + dev.getDescription());
            }
            Scanner scanner = new Scanner(System.in);
            nif = Pcaps.getDevByName(scanner.nextLine());
            if (nif == null) {
                throw new IllegalStateException(
                        "Interface de rede não encontrada: " + interfaceName
                );
            }
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
                "[com.deathnode.monitor.Sniffer] Listening on " + nif.getName()
        );

        handle.loop(-1, listener);
    }

    private void processPacket(Packet packet) {
        System.out.println("Packet class: " + packet.getClass().getName());
        System.out.println("Packet: " + packet);

        // Navega pelas camadas do pacote
        IpV4Packet ipPacket = packet.get(IpV4Packet.class);

        if (ipPacket == null) {
            return;
        }

        TcpPacket tcpPacket = ipPacket.get(TcpPacket.class);

        if (tcpPacket == null) {
            return;
        }

        System.out.println("Processing packet: " + tcpPacket);

        String srcIp = ipPacket.getHeader().getSrcAddr().getHostAddress();
        String dstIp = ipPacket.getHeader().getDstAddr().getHostAddress();
        int packetSize = packet.length();

        aggregator.record(srcIp, packetSize);
    }
}
