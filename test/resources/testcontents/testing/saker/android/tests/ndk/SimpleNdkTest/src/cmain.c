#include <android/log.h>
#include <stdlib.h>

int mycfunction() {
	__android_log_write(ANDROID_LOG_FATAL, "tag", "message");
	//to test c++ linking
	return *(int*) malloc(123);
}
