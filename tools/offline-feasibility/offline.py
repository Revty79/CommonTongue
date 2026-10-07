"""Python network tripwire; use an OS network block for native subprocess proof."""

import socket
import sys


def probe():
    observations = {}
    for host in ("github.com", "huggingface.co"):
        try:
            with socket.create_connection((host, 443), timeout=3):
                observations[host] = "AVAILABLE"
        except OSError as error:
            observations[host] = str(error)
    return observations


def install_tripwire():
    events = []

    def deny(event, args):
        if event in ("socket.connect", "socket.connect_ex", "socket.getaddrinfo", "socket.sendto"):
            events.append(event)
            raise RuntimeError(f"Offline execution forbids {event}")

    sys.addaudithook(deny)
    return events
