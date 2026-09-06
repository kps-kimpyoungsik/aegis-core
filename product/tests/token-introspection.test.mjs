import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import { createOidcJwtAuthenticator } from "../apps/api-server/oidc-jwt-authenticator.js";
import { createTokenIntrospectionVerifier } from "../apps/api-server/token-introspection.js";

const ISSUER="https://identity.example.test";
const AUDIENCE="aegis-api";
const JWKS_URI="https://identity.example.test/keys";
const INTROSPECTION_URI="https://identity.example.test/introspect";
const NOW_MS=Date.parse("2026-09-06T00:00:00Z");
const NOW_SECONDS=Math.floor(NOW_MS/1000);

function signingMaterial(){const{privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});const jwk=publicKey.export({format:"jwk"});return{privateKey,kid:"r1-15",jwk:{...jwk,kid:"r1-15",alg:"RS256",use:"sig"}};}
function encode(value){return Buffer.from(JSON.stringify(value),"utf8").toString("base64url");}
function tokenFor(key){const header=encode({alg:"RS256",kid:key.kid});const claims=encode({iss:ISSUER,aud:AUDIENCE,sub:"principal:r1-15",tenant_id:"tenant-a",roles:["RUNTIME_VIEWER"],iat:NOW_SECONDS-10,exp:NOW_SECONDS+300});const input=`${header}.${claims}`;const signature=sign("RSA-SHA256",Buffer.from(input),key.privateKey).toString("base64url");return`${input}.${signature}`;}
const response=(body,status=200)=>({ok:status>=200&&status<300,status,async json(){return body;}});

function oidcFetch(key){return async(url,options)=>{assert.equal(options.redirect,"error");if(url===`${ISSUER}/.well-known/openid-configuration`)return response({issuer:ISSUER,jwks_uri:JWKS_URI});if(url===JWKS_URI)return response({keys:[key.jwk]});return response({},404);};}

test("active introspection response allows already-verified token",async()=>{const key=signingMaterial();let calls=0;const verifier=createTokenIntrospectionVerifier({endpoint:INTROSPECTION_URI,clientId:"client",clientSecret:"secret",fetchImpl:async(url,options)=>{calls+=1;assert.equal(url,INTROSPECTION_URI);assert.equal(options.method,"POST");assert.equal(options.redirect,"error");assert.match(options.headers.authorization,/^Basic /);assert.match(options.body,/token=/);return response({active:true,sub:"principal:r1-15"});}});const authenticate=createOidcJwtAuthenticator({issuer:ISSUER,audience:AUDIENCE,fetchImpl:oidcFetch(key),now:()=>NOW_MS,tokenStatusVerifier:verifier});const principal=await authenticate({headers:{authorization:`Bearer ${tokenFor(key)}`}});assert.equal(principal.id,"principal:r1-15");assert.equal(calls,1);});

test("inactive revoked token fails closed after valid signature",async()=>{const key=signingMaterial();const verifier=createTokenIntrospectionVerifier({endpoint:INTROSPECTION_URI,clientId:"client",clientSecret:"secret",fetchImpl:async()=>response({active:false})});const authenticate=createOidcJwtAuthenticator({issuer:ISSUER,audience:AUDIENCE,fetchImpl:oidcFetch(key),now:()=>NOW_MS,tokenStatusVerifier:verifier});assert.equal(await authenticate({headers:{authorization:`Bearer ${tokenFor(key)}`}}),null);});

test("introspection subject mismatch and provider failure fail closed",async()=>{for(const fetchImpl of[async()=>response({active:true,sub:"principal:other"}),async()=>response({},503),async()=>{throw new Error("network");}]){const verifier=createTokenIntrospectionVerifier({endpoint:INTROSPECTION_URI,clientId:"client",clientSecret:"secret",fetchImpl});assert.equal(await verifier({token:"opaque",claims:{sub:"principal:r1-15"},principal:{id:"principal:r1-15"}}),false);}});

test("introspection endpoint and configuration are bounded and HTTPS-only",()=>{assert.throws(()=>createTokenIntrospectionVerifier({endpoint:"http://identity.example.test/introspect",clientId:"c",clientSecret:"s"}),/HTTPS_ENDPOINT_REQUIRED/);assert.throws(()=>createTokenIntrospectionVerifier({endpoint:INTROSPECTION_URI,clientId:"",clientSecret:"s"}),/CLIENT_ID_REQUIRED/);assert.throws(()=>createTokenIntrospectionVerifier({endpoint:INTROSPECTION_URI,clientId:"c",clientSecret:"s",timeoutMs:50}),/TIMEOUT_INVALID/);});
