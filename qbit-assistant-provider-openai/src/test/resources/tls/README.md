# Test-only TLS fixtures

These certificates and the private key in `server.p12` are synthetic test material.
They must never be used for deployment or trusted outside tests. The PKCS12
password is `test-only`. The CA private key is not retained in the repository.
BouncyCastle was not on the test classpath, so fixtures were generated with the
JDK 21 **keytool executable**, not internal `sun.security` APIs.

The leaf is signed by `QBit TEST ONLY Root CA` and has only `DNS:localhost` in its
SAN; connecting to `127.0.0.1` deliberately fails hostname verification. Both
certificates start on 2020-01-01 and last 36,500 days to avoid date-dependent tests.
Each regeneration creates fresh random keys. Run in a temporary directory, then
copy only `ca.pem` and `server.p12` here:

```sh
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -dname 'CN=QBit TEST ONLY Root CA' -ext bc:c=ca:true -ext ku:c=keyCertSign,cRLSign -startdate '2020/01/01 00:00:00' -validity 36500 -keystore ca.p12 -storepass test-only -storetype PKCS12 -noprompt
keytool -exportcert -rfc -alias ca -keystore ca.p12 -storepass test-only -file ca.pem
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -dname 'CN=localhost' -startdate '2020/01/01 00:00:00' -validity 36500 -keystore server.p12 -storetype PKCS12 -storepass test-only -noprompt
keytool -certreq -alias server -keystore server.p12 -storepass test-only -file server.csr
keytool -gencert -alias ca -keystore ca.p12 -storepass test-only -infile server.csr -outfile server.pem -rfc -startdate '2020/01/01 00:00:00' -validity 36500 -ext san=dns:localhost -ext eku=serverAuth -ext ku=digitalSignature,keyEncipherment
keytool -importcert -alias ca -file ca.pem -keystore server.p12 -storepass test-only -noprompt
keytool -importcert -alias server -file server.pem -keystore server.p12 -storepass test-only -noprompt
```
