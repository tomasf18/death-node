#!/usr/bin/env python3
"""
Network Rate Limiter - Gateway Protection
Blocks sources that exceed packet rate threshold
"""

from scapy.all import sniff, IP
from collections import defaultdict, deque
import threading
import time
import os
import sys
import subprocess

# Configuration
TIME_WINDOW = 30  # seconds
PACKET_THRESHOLD = 1000  # packets per TIME_WINDOW
BLOCK_DURATION = 30  # seconds to block
INTERFACES = ['eth1','eth2']

# Statistics storage
packet_history = deque()
blocked_ips = {}  # {ip: unblock_time}
stats_lock = threading.Lock()

def run_command(cmd):
    """Execute shell command"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        return result.returncode == 0
    except Exception as e:
        print(f"[!] Command error: {e}")
        return False

def block_ip(ip_address):
    """Block IP using iptables"""
    with stats_lock:
        if ip_address in blocked_ips:
            return  # Already blocked
        
        blocked_ips[ip_address] = time.time() + BLOCK_DURATION
    
    # Add iptables rule to DROP packets from this IP
    cmd = f"iptables -I FORWARD -s {ip_address} -j DROP"
    if run_command(cmd):
        print(f"\n[BLOCKED] {ip_address} - Exceeded {PACKET_THRESHOLD} packets in {TIME_WINDOW}s")
    else:
        print(f"\n[ERROR] Failed to block {ip_address}")

def unblock_ip(ip_address):
    """Unblock IP by removing iptables rule"""
    cmd = f"iptables -D FORWARD -s {ip_address} -j DROP"
    if run_command(cmd):
        print(f"\n[UNBLOCKED] {ip_address} - Block duration expired")
    else:
        print(f"\n[ERROR] Failed to unblock {ip_address}")
    
    with stats_lock:
        if ip_address in blocked_ips:
            del blocked_ips[ip_address]

def check_and_unblock():
    """Check if any IPs should be unblocked"""
    current_time = time.time()
    to_unblock = []
    
    with stats_lock:
        for ip, unblock_time in list(blocked_ips.items()):
            if current_time >= unblock_time:
                to_unblock.append(ip)
    
    for ip in to_unblock:
        unblock_ip(ip)

def packet_handler(packet):
    """Process each captured packet"""
    if packet.haslayer(IP):
        src_ip = packet[IP].src
        packet_size = len(packet)
        current_time = time.time()
        
        # Don't count packets from already blocked IPs
        with stats_lock:
            if src_ip in blocked_ips:
                return
        
        with stats_lock:
            packet_history.append({
                'src_ip': src_ip,
                'bytes': packet_size,
                'timestamp': current_time
            })

def calculate_stats():
    """Calculate statistics and check for rate limit violations"""
    current_time = time.time()
    cutoff_time = current_time - TIME_WINDOW
    
    stats = defaultdict(lambda: {'packets': 0, 'bytes': 0})
    
    with stats_lock:
        # Remove old packets
        while packet_history and packet_history[0]['timestamp'] < cutoff_time:
            packet_history.popleft()
        
        # Calculate stats for remaining packets
        for packet_data in packet_history:
            src_ip = packet_data['src_ip']
            stats[src_ip]['packets'] += 1
            stats[src_ip]['bytes'] += packet_data['bytes']
    
    # Check for violations
    for src_ip, data in stats.items():
        if data['packets'] > PACKET_THRESHOLD:
            # Check if not already blocked
            with stats_lock:
                if src_ip not in blocked_ips:
                    block_ip(src_ip)
    
    return stats

def sniffer_thread(interface):
    """Sniff packets on a specific interface"""
    print(f"[+] Starting sniffer on {interface}...")
    try:
        sniff(iface=interface, prn=packet_handler, store=False)
    except Exception as e:
        print(f"[!] Error on {interface}: {e}")
        sys.exit(1)

def format_bytes(bytes_num):
    """Format bytes to human readable format"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes_num < 1024.0:
            return f"{bytes_num:.2f} {unit}"
        bytes_num /= 1024.0
    return f"{bytes_num:.2f} TB"

def display_stats():
    """Display statistics table"""
    while True:
        time.sleep(1)  # Update every second
        
        # Check for IPs to unblock
        check_and_unblock()
        
        # Clear screen
        os.system('clear' if os.name == 'posix' else 'cls')
        
        # Header
        print("=" * 95)
        print(f"  DeathNode Monitor (Threshold: {PACKET_THRESHOLD} pkts/{TIME_WINDOW}s)")
        print("=" * 95)
        print(f"{'Source IP':<20} {'Packets':<12} {'Bytes':<15} {'Formatted':<12} {'Status':<15}")
        print("-" * 95)
        
        # Get current stats
        stats_copy = calculate_stats()
        
        if not stats_copy:
            print("  No traffic detected in the last 30 seconds...")
        else:
            # Sort by packet count (descending)
            sorted_stats = sorted(stats_copy.items(), 
                                key=lambda x: x[1]['packets'], 
                                reverse=True)
            
            total_packets = 0
            total_bytes = 0
            
            for src_ip, data in sorted_stats:
                packets = data['packets']
                bytes_total = data['bytes']
                
                total_packets += packets
                total_bytes += bytes_total
                
                # Determine status
                with stats_lock:
                    if src_ip in blocked_ips:
                        time_left = int(blocked_ips[src_ip] - time.time())
                        status = f"🔒 BLOCKED ({time_left}s)"
                    elif packets > PACKET_THRESHOLD * 0.8:
                        status = "⚠️  WARNING"
                    else:
                        status = "✅ OK"
                
                print(f"{src_ip:<20} {packets:<12} {bytes_total:<15} {format_bytes(bytes_total):<12} {status:<15}")
            
            # Summary
            print("-" * 95)
            print(f"{'TOTAL':<20} {total_packets:<12} {total_bytes:<15} {format_bytes(total_bytes):<12}")
        
        # Blocked IPs section
        with stats_lock:
            if blocked_ips:
                print("=" * 95)
                print("  BLOCKED IPs:")
                print("-" * 95)
                for ip, unblock_time in blocked_ips.items():
                    time_left = int(unblock_time - time.time())
                    print(f"  🔒 {ip:<20} - Unblocks in {time_left}s")
        
        print("=" * 95)
        print("Press Ctrl+C to stop")

def cleanup_iptables():
    """Remove all blocking rules on exit"""
    print("\n[+] Cleaning up iptables rules...")
    with stats_lock:
        for ip in list(blocked_ips.keys()):
            cmd = f"iptables -D FORWARD -s {ip} -j DROP"
            run_command(cmd)
            print(f"[+] Removed rule for {ip}")

def main():
    """Main function"""
    print("[+] Network Rate Limiter - Gateway Protection")
    print(f"[+] Monitoring interfaces: {', '.join(INTERFACES)}")
    print(f"[+] Threshold: {PACKET_THRESHOLD} packets per {TIME_WINDOW} seconds")
    print(f"[+] Block duration: {BLOCK_DURATION} seconds")
    print("[+] Starting sniffers...\n")
    
    # Start sniffer threads for each interface
    threads = []
    for iface in INTERFACES:
        t = threading.Thread(target=sniffer_thread, args=(iface,), daemon=True)
        t.start()
        threads.append(t)
    
    # Give sniffers time to start
    time.sleep(2)
    
    # Start display
    try:
        display_stats()
    except KeyboardInterrupt:
        print("\n\n[+] Stopping rate limiter...")
        cleanup_iptables()
        print("[+] Cleanup complete")
        sys.exit(0)

if __name__ == "__main__":
    # Check if running as root
    if os.geteuid() != 0:
        print("[!] This script must be run as root (sudo)")
        print("[!] Usage: sudo python3 rate_limiter.py")
        sys.exit(1)
    
    main()