import http.server
import json
import os
import sys
import time

HOST = "0.0.0.0"
PORT = int(os.environ.get("PORT", 8081))


class MockLLMHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        response_body = json.dumps({"status": "healthy"}).encode("utf-8")
        self.send_header("Content-Length", str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body)

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > 0:
            _ = self.rfile.read(content_length)

        # Simulate 30ms LLM processing latency
        time.sleep(0.030)

        response_body = json.dumps({"choices": [{"text": "Mock LLM response"}]}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body)

    def log_message(self, format, *args):
        sys.stdout.write(f"[{self.log_date_time_string()}] {self.address_string()} - {format % args}\n")
        sys.stdout.flush()


if __name__ == "__main__":
    server = http.server.ThreadingHTTPServer((HOST, PORT), MockLLMHandler)
    print(f"Mock LLM upstream server listening on {HOST}:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
