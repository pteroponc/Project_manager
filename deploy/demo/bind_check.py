"""Strict, side-effect-free parsing of numeric ss endpoints for deployment safety checks."""
import ipaddress
import re

ALLOWED = ipaddress.IPv4Address("127.0.0.1")


def parse_endpoint(endpoint):
    host, sep, port_text = endpoint.rpartition(":")
    if not sep or not re.fullmatch(r"[0-9]+", port_text):
        raise ValueError("Non-numeric endpoint: " + endpoint)
    port = int(port_text)
    if not 1 <= port <= 65535 or str(port) != port_text:
        raise ValueError("Invalid port: " + endpoint)
    if host.startswith("[") and host.endswith("]"):
        host = host[1:-1]
    if any(char in host for char in "[]%") or not host:
        raise ValueError("Invalid numeric host: " + endpoint)
    address = ipaddress.ip_address(host)
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped is not None:
        address = address.ipv4_mapped
    return address, port


def is_loopback_endpoint(endpoint):
    address, _port = parse_endpoint(endpoint)
    return address.is_loopback


def validate_listeners(inventory, pid, port=18080):
    """Use full ss -H -ltnp inventory, not a port-filtered subset.
    Exactly one socket may belong to the application or occupy its expected port.
    Other application-PID sockets, unknown owners and shared owners fail closed.
    """
    if not isinstance(pid, int) or isinstance(pid, bool) or pid <= 0:
        raise ValueError("Invalid expected PID")
    if not isinstance(port, int) or isinstance(port, bool) or not 1024 <= port <= 65535:
        raise ValueError("Invalid expected app port")
    relevant = []
    for line in inventory.splitlines():
        if not line.strip():
            continue
        fields = line.split()
        if len(fields) < 5 or fields[0] != "LISTEN":
            raise ValueError("Unrecognized ss listener row: " + line)
        address, actual_port = parse_endpoint(fields[3])
        owners = {int(v) for v in re.findall(r"\bpid=([0-9]+),", " ".join(fields[5:]))}
        if actual_port == port or pid in owners:
            relevant.append((line, address, actual_port, owners))
    if len(relevant) != 1:
        raise ValueError("Expected exactly one application listener: " + inventory)
    line, address, actual_port, owners = relevant[0]
    if address != ALLOWED or actual_port != port or owners != {pid}:
        raise ValueError("Unsafe application listener: " + line)
    return line


def validate_windows_listeners(inventory, pid, port):
    if not isinstance(inventory, list):
        raise ValueError("Expected complete Windows listener inventory")
    rows = []
    for item in inventory:
        if set(item) != {"LocalAddress", "LocalPort", "OwningProcess"}:
            raise ValueError("Incomplete Windows listener metadata")
        owner, actual_port = item["OwningProcess"], item["LocalPort"]
        if type(owner) is not int or owner < 0 or type(actual_port) is not int:
            raise ValueError("Invalid Windows owner/port")
        address, _ = parse_endpoint(str(item["LocalAddress"]) + ":" + str(actual_port))
        rows.append('LISTEN 0 0 ' + str(address) + ':' + str(actual_port) +
                    ' *:* users:(("process",pid=' + str(owner) + ',fd=0))')
    return validate_listeners("\n".join(rows), pid, port)
