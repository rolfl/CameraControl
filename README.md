CameraControl
=============

This is a mock up of what the camera control datagram process could look like.

The only 'real' class is CameraControl. This class opens a Datagram channel, and uses a single thread to
manage the remote camera.

The other classes are what I imagine your support classes look like, and then I have a 'dummy' camera to test against as well.

Start the DummyCam class running in one Java process, then run the CameraTest class to communicate with it. The dummy will intentionally fail about 10% of the time