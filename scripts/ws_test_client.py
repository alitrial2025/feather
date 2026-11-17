"""Small RFC6455 text client for local smoke tests; no third-party dependencies."""
import base64
import hashlib
import json
import os
import socket
import struct
import time


class WebSocket:
    def __init__(self, port, path="/ws"):
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=5)
        key = base64.b64encode(os.urandom(16)).decode()
        self.socket.sendall((f"GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n"
                             "Connection: Upgrade\r\nUpgrade: websocket\r\n"
                             f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        headers = b""
        while not headers.endswith(b"\r\n\r\n"):
            headers += self._read(1)
            assert len(headers) < 16384
        assert headers.startswith(b"HTTP/1.1 101"), headers
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        assert accept in headers

    def _read(self, length):
        chunks = bytearray()
        while len(chunks) < length:
            chunk = self.socket.recv(length - len(chunks))
            if not chunk:
                raise AssertionError("WebSocket closed unexpectedly")
            chunks.extend(chunk)
        return bytes(chunks)

    def _send(self, payload, opcode=1):
        mask = os.urandom(4)
        length = len(payload)
        header = bytes([0x80 | opcode])
        if length < 126:
            header += bytes([0x80 | length])
        elif length <= 65535:
            header += bytes([0x80 | 126]) + struct.pack("!H", length)
        else:
            header += bytes([0x80 | 127]) + struct.pack("!Q", length)
        self.socket.sendall(header + mask + bytes(value ^ mask[i % 4] for i, value in enumerate(payload)))

    def send_json(self, value):
        self._send(json.dumps(value).encode())

    def receive_until(self, predicate, timeout=8):
        deadline = time.monotonic() + timeout
        seen = []
        while time.monotonic() < deadline:
            self.socket.settimeout(max(0.1, deadline - time.monotonic()))
            first, second = self._read(2)
            assert first & 0x80 and not second & 0x80, "Unexpected server framing"
            length = second & 0x7f
            if length == 126:
                length = struct.unpack("!H", self._read(2))[0]
            elif length == 127:
                length = struct.unpack("!Q", self._read(8))[0]
            assert length < 16 * 1024 * 1024
            payload = self._read(length)
            opcode = first & 15
            if opcode == 9:
                self._send(payload, 10)
                continue
            assert opcode == 1, (opcode, payload)
            value = json.loads(payload)
            seen.append(value.get("type"))
            if predicate(value):
                return value
        raise AssertionError(f"Expected message not received; saw {seen}")

    def close(self):
        self.socket.close()
