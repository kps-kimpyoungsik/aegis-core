import http from "node:http";
const server = http.createServer((req,res)=>{
  if (req.url === "/health/live") { res.writeHead(200,{"content-type":"application/json"}); return res.end(JSON.stringify({status:"HEALTHY"})); }
  if (req.url === "/health/ready") { res.writeHead(200,{"content-type":"application/json"}); return res.end(JSON.stringify({status:"READY",contracts:"0.1.0"})); }
  res.writeHead(404); res.end();
});
if (process.env.NODE_ENV !== "test") server.listen(Number(process.env.PORT || 8080));
export { server };
