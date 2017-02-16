PORT=8887
while `true` ; do
	echo "about to start jetty on port $PORT"
	mvn -Djetty.port=$PORT jetty:run
	SLEEPTIME=$?
	echo "jetty stopped. Going to sleep for $SLEEPTIME seconds"
	sleep $SLEEPTIME
done

