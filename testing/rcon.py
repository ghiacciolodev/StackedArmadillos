"""Minimal RCON client used by the test script."""
import re
import socket
import struct


class Rcon:
    def __init__(self, host, port, password):
        self.sock = socket.create_connection((host, port))
        self._send(1, 3, password)
        if self._read()[0] == -1:
            raise SystemExit('RCON login failed, check the password')

    def _send(self, request_id, kind, body):
        data = struct.pack('<ii', request_id, kind) + body.encode() + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(data)) + data)

    def _read(self):
        length = struct.unpack('<i', self._recv(4))[0]
        data = self._recv(length)
        request_id = struct.unpack('<i', data[:4])[0]
        return request_id, data[8:-2].decode(errors='replace')

    def _recv(self, n):
        buf = b''
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError('RCON connection closed')
            buf += chunk
        return buf

    def __call__(self, command):
        """Runs a command and returns its output without color codes."""
        self._send(2, 2, command)
        return re.sub('§.', '', self._read()[1]).strip()

