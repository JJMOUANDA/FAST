FROM tomcat:9.0.70-jre8

# timescaledb-parallel-copy installation
RUN apt-get update && \
    apt-get install -y wget gnupg2 lsb-release && \
    echo "deb https://packagecloud.io/timescale/timescaledb/ubuntu focal main" | tee /etc/apt/sources.list.d/timescaledb.list && \
    wget --quiet -O - https://packagecloud.io/timescale/timescaledb/gpgkey | apt-key add - && \
    apt-get update && \
    apt-get install -y timescaledb-tools && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

CMD ["catalina.sh", "run"]