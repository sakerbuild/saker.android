#include <android/log.h>

int myfunction(){
	__android_log_write(ANDROID_LOG_FATAL, "tag", "message");
	//to test c++ linking
	return *new int(123);
}
