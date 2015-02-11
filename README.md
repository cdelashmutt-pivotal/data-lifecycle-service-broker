

#Test Env Setup

- Create a VPC
- Create a new ubuntu machine (t1.micro is fine) 
- apt-get update
- Follow the [postgres install instrcutions](https://help.ubuntu.com/community/PostgreSQL)
- apt-get install postgressql
- change the listen_address to * in /etc/postgresql/<version>/main/postgresql.conf
- allow remote connections to pg_hba.conf
- host    all             all             0.0.0.0/0               md5
- connect w/ plsql or pgadmin and add some test data.
