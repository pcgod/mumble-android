%module celt
%{
/** Contains the state of an encoder. One encoder state is needed 
    for each stream. It is initialised once at the beginning of the
    stream. Do *not* re-initialise the state for every frame.
   @brief Encoder state
 */
typedef struct CELTEncoder CELTEncoder;

/** State of the decoder. One decoder state is needed for each stream.
    It is initialised once at the beginning of the stream. Do *not*
    re-initialise the state for every frame */
typedef struct CELTDecoder CELTDecoder;

/** The mode contains all the information necessary to create an
    encoder. Both the encoder and decoder need to be initialised
    with exactly the same mode, otherwise the quality will be very 
    bad */
typedef struct CELTMode CELTMode;

extern CELTMode *celt_mode_create(int Fs, int frame_size, int *error);
extern void celt_mode_destroy(CELTMode *mode);

extern CELTEncoder *celt_encoder_create(const CELTMode *mode, int channels, int *error);
extern void celt_encoder_destroy(CELTEncoder *st);
extern int celt_encode(CELTEncoder *st, const short *pcm, short *optional_synthesis, unsigned char *compressed, int nbCompressedBytes);
extern int celt_encoder_ctl(CELTEncoder * st, int request, ...);

extern CELTDecoder *celt_decoder_create(const CELTMode *mode, int channels, int *error);
extern void celt_decoder_destroy(CELTDecoder *st);
extern int celt_decode(CELTDecoder *st, const unsigned char *data, int len, short *pcm);
//extern int celt_decoder_ctl(CELTDecoder * st, int request, ...);
%}
%include "typemaps.i"
%include "arrays_java.i"

%apply unsigned char[] { unsigned char * };
%apply float[] { float * };
%apply short[] { short * };

%apply int *OUTPUT { int *error };
%apply int *OUTPUT { int *value };

#define EXPORT extern
#define celt_int32 int
#define celt_int16 short

#define _celt_check_int(x) (((void)((x) == (celt_int32)0)), (celt_int32)(x))
#define _celt_check_mode_ptr_ptr(ptr) ((ptr) + ((ptr) - (CELTMode**)(ptr)))

/* Error codes */
/** No error */
#define CELT_OK                0
/** An (or more) invalid argument (e.g. out of range) */
#define CELT_BAD_ARG          -1
/** The mode struct passed is invalid */
#define CELT_INVALID_MODE     -2
/** An internal error was detected */
#define CELT_INTERNAL_ERROR   -3
/** The data passed (e.g. compressed data to decoder) is corrupted */
#define CELT_CORRUPTED_DATA   -4
/** Invalid/unsupported request number */
#define CELT_UNIMPLEMENTED    -5
/** An encoder or decoder structure is invalid or already freed */
#define CELT_INVALID_STATE    -6
/** Memory allocation has failed */
#define CELT_ALLOC_FAIL       -7

/* Requests */
#define CELT_GET_MODE_REQUEST    1
/** Get the CELTMode used by an encoder or decoder */
#define CELT_GET_MODE(x) CELT_GET_MODE_REQUEST, _celt_check_mode_ptr_ptr(x)
#define CELT_SET_COMPLEXITY_REQUEST    2
/** Controls the complexity from 0-10 (int) */
#define CELT_SET_COMPLEXITY(x) CELT_SET_COMPLEXITY_REQUEST, _celt_check_int(x)
#define CELT_SET_PREDICTION_REQUEST    4
/** Controls the use of interframe prediction.
    0=Independent frames
    1=Short term interframe prediction allowed
    2=Long term prediction allowed
 */
#define CELT_SET_PREDICTION(x) CELT_SET_PREDICTION_REQUEST, _celt_check_int(x)
#define CELT_SET_VBR_RATE_REQUEST    6
/** Set the target VBR rate in bits per second(int); 0=CBR (default) */
#define CELT_SET_VBR_RATE(x) CELT_SET_VBR_RATE_REQUEST, _celt_check_int(x)
/** Reset the encoder/decoder memories to zero*/
#define CELT_RESET_STATE_REQUEST        8
#define CELT_RESET_STATE       CELT_RESET_STATE_REQUEST

/** GET the frame size used in the current mode */
#define CELT_GET_FRAME_SIZE   1000
/** GET the lookahead used in the current mode */
#define CELT_GET_LOOKAHEAD    1001
/** GET the sample rate used in the current mode */
#define CELT_GET_SAMPLE_RATE  1003

/** GET the bit-stream version for compatibility check */
#define CELT_GET_BITSTREAM_VERSION 2000


/** Contains the state of an encoder. One encoder state is needed 
    for each stream. It is initialised once at the beginning of the
    stream. Do *not* re-initialise the state for every frame.
   @brief Encoder state
 */
typedef struct CELTEncoder CELTEncoder;

/** State of the decoder. One decoder state is needed for each stream.
    It is initialised once at the beginning of the stream. Do *not*
    re-initialise the state for every frame */
typedef struct CELTDecoder CELTDecoder;

/** The mode contains all the information necessary to create an
    encoder. Both the encoder and decoder need to be initialised
    with exactly the same mode, otherwise the quality will be very 
    bad */
typedef struct CELTMode CELTMode;


/** \defgroup codec Encoding and decoding */
/*  @{ */

/* Mode calls */

/** Creates a new mode struct. This will be passed to an encoder or 
    decoder. The mode MUST NOT BE DESTROYED until the encoders and 
    decoders that use it are destroyed as well.
 @param Fs Sampling rate (32000 to 96000 Hz)
 @param frame_size Number of samples (per channel) to encode in each 
                   packet (even values; 64 - 512)
 @param error Returned error code (if NULL, no error will be returned)
 @return A newly created mode
*/
EXPORT CELTMode *celt_mode_create(celt_int32 Fs, int frame_size, int *error);

/** Destroys a mode struct. Only call this after all encoders and 
    decoders using this mode are destroyed as well.
 @param mode Mode to be destroyed
*/
EXPORT void celt_mode_destroy(CELTMode *mode);

/** Query information from a mode */
EXPORT int celt_mode_info(const CELTMode *mode, int request, celt_int32 *value);

/* Encoder stuff */


/** Creates a new encoder state. Each stream needs its own encoder 
    state (can't be shared across simultaneous streams).
 @param mode Contains all the information about the characteristics of
 *  the stream (must be the same characteristics as used for the 
 *  decoder)
 @param channels Number of channels
 @param error Returns an error code
 @return Newly created encoder state.
*/
EXPORT CELTEncoder *celt_encoder_create(const CELTMode *mode, int channels, int *error);

/** Destroys a an encoder state.
 @param st Encoder state to be destroyed
 */
EXPORT void celt_encoder_destroy(CELTEncoder *st);

/** Encodes a frame of audio.
 @param st Encoder state
 @param pcm PCM audio in float format, with a normal range of ±1.0. 
 *          Samples with a range beyond ±1.0 are supported but will 
 *          be clipped by decoders using the integer API and should 
 *          only be used if it is known that the far end supports 
 *          extended dynmaic range. There must be exactly
 *          frame_size samples per channel. 
 @param optional_synthesis If not NULL, the encoder copies the audio signal that
 *          the decoder would decode. It is the same as calling the
 *          decoder on the compressed data, just faster.
 *          This may alias pcm. 
 @param compressed The compressed data is written here. This may not alias pcm or
 *                 optional_synthesis.
 @param nbCompressedBytes Maximum number of bytes to use for compressing the frame
 *          (can change from one frame to another)
 @return Number of bytes written to "compressed". Will be the same as 
 *       "nbCompressedBytes" unless the stream is VBR and will never be larger.
 *       If negative, an error has occurred (see error codes). It is IMPORTANT that
 *       the length returned be somehow transmitted to the decoder. Otherwise, no
 *       decoding is possible.
*/
//EXPORT int celt_encode_float(CELTEncoder *st, float *pcm, float *optional_synthesis, char *compressed, int nbCompressedBytes);

/** Encodes a frame of audio.
 @param st Encoder state
 @param pcm PCM audio in signed 16-bit format (native endian). There must be 
 *          exactly frame_size samples per channel. 
 @param optional_synthesis If not NULL, the encoder copies the audio signal that
 *                         the decoder would decode. It is the same as calling the
 *                         decoder on the compressed data, just faster.
 *                         This may alias pcm. 
 @param compressed The compressed data is written here. This may not alias pcm or
 *                         optional_synthesis.
 @param nbCompressedBytes Maximum number of bytes to use for compressing the frame
 *                        (can change from one frame to another)
 @return Number of bytes written to "compressed". Will be the same as 
 *       "nbCompressedBytes" unless the stream is VBR and will never be larger.
 *       If negative, an error has occurred (see error codes). It is IMPORTANT that
 *       the length returned be somehow transmitted to the decoder. Otherwise, no
 *       decoding is possible.
 */
EXPORT int celt_encode(CELTEncoder *st, celt_int16 *pcm, celt_int16 *optional_synthesis, unsigned char *compressed, int nbCompressedBytes);

/** Query and set encoder parameters 
 @param st Encoder state
 @param request Parameter to change or query
 @param value Pointer to a 32-bit int value
 @return Error code
*/
EXPORT int celt_encoder_ctl(CELTEncoder * st, int request, int value);

/* Decoder stuff */


/** Creates a new decoder state. Each stream needs its own decoder state (can't
    be shared across simultaneous streams).
 @param mode Contains all the information about the characteristics of the
             stream (must be the same characteristics as used for the encoder)
 @param channels Number of channels
 @param error Returns an error code
 @return Newly created decoder state.
 */
EXPORT CELTDecoder *celt_decoder_create(const CELTMode *mode, int channels, int *error);

/** Destroys a a decoder state.
 @param st Decoder state to be destroyed
 */
EXPORT void celt_decoder_destroy(CELTDecoder *st);

/** Decodes a frame of audio.
 @param st Decoder state
 @param data Compressed data produced by an encoder
 @param len Number of bytes to read from "data". This MUST be exactly the number
            of bytes returned by the encoder. Using a larger value WILL NOT WORK.
 @param pcm One frame (frame_size samples per channel) of decoded PCM will be
            returned here in float format. 
 @return Error code.
   */
//EXPORT int celt_decode_float(CELTDecoder *st, unsigned char *data, int len, float *pcm);

/** Decodes a frame of audio.
 @param st Decoder state
 @param data Compressed data produced by an encoder
 @param len Number of bytes to read from "data". This MUST be exactly the number
            of bytes returned by the encoder. Using a larger value WILL NOT WORK.
 @param pcm One frame (frame_size samples per channel) of decoded PCM will be
            returned here in 16-bit PCM format (native endian). 
 @return Error code.
 */
EXPORT int celt_decode(CELTDecoder *st, unsigned char *data, int len, celt_int16 *pcm);

/** Query and set decoder parameters
   @param st Decoder state
   @param request Parameter to change or query
   @param value Pointer to a 32-bit int value
   @return Error code
 */
//EXPORT int celt_decoder_ctl(CELTDecoder * st, int request, ...);


/** Returns the English string that corresponds to an error code
 * @param error Error code (negative for an error, 0 for success
 * @return Constant string (must NOT be freed)
 */
//EXPORT const char *celt_strerror(int error);
