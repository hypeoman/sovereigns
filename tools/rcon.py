# usage: rcon.py <commands-file>; lines "wait N" sleep, others are sent via RCON (localhost:25575) and replies printed.
import socket,struct,sys,time
def pkt(s,i,t,body):
    b=body.encode()+b'\0\0'; s.sendall(struct.pack('<iii',len(b)+8,i,t)+b)
def recv(s):
    n=struct.unpack('<i',s.recv(4))[0]; d=b''
    while len(d)<n: d+=s.recv(n-len(d))
    return d[8:-2].decode('utf8','replace')
for _ in range(120):
    try: s=socket.create_connection(('127.0.0.1',25575)); break
    except OSError: time.sleep(5)
pkt(s,1,3,'svdev'); recv(s)
for line in open(sys.argv[1]):
    line=line.strip()
    if not line: continue
    if line.startswith('wait'): time.sleep(int(line.split()[1])); continue
    pkt(s,2,2,line); print('>',line,'|',recv(s),flush=True)
