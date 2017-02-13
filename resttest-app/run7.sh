PORT=8887
while `true` ; do
	echo "about to start jetty on port $PORT"
	mvn -Djetty.port=$PORT jetty:run
	echo "jetty stopped. Going to sleep for 30 seconds"
	sleep 30
done

