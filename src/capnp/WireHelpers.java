package org.capnproto;

final class WireHelpers {

    public static StructReader readStructPointer(SegmentReader segment,
                                                 int refOffset,
                                                 int nestingLimit) {

        // TODO error handling

        long ref = WirePointer.get(segment.buffer, refOffset);
        int ptrOffset = WirePointer.target(refOffset, ref);
        int structPtr = WirePointer.structPointer(ref);
        int dataSizeWords = StructPointer.dataSize(structPtr);

        return new StructReader(segment,
                                ptrOffset * 8,
                                (ptrOffset + dataSizeWords),
                                dataSizeWords * 64,
                                StructPointer.ptrCount(structPtr),
                                (byte)0,
                                nestingLimit - 1);

    }


    public static ListReader readListPointer(SegmentReader segment,
                                             int refOffset,
                                             byte expectedElementSize,
                                             int nestingLimit) {

        long ref = WirePointer.get(segment.buffer, refOffset);

        // TODO check for null, follow fars, nestingLimit
        if (WirePointer.isNull(ref)) {
            return new ListReader();
        }

        int listPtr = WirePointer.listPointer(ref);

        int ptrOffset = WirePointer.target(refOffset, ref);
        long ptr = WirePointer.get(segment.buffer, ptrOffset);

        switch (ListPointer.elementSize(listPtr)) {
        case FieldSize.INLINE_COMPOSITE : {
            int wordCount = ListPointer.inlineCompositeWordCount(listPtr);

            long tag = ptr;
            ptrOffset += 1;

            // TODO bounds check

            int size = WirePointer.inlineCompositeListElementCount(tag);

            int structPtr = WirePointer.structPointer(tag);
            int wordsPerElement = StructPointer.wordSize(structPtr);

            // TODO check that elemements do not overrun word count

            // TODO check whether the size is compatible

            return new ListReader(segment,    // TODO follow fars
                                  ptrOffset * 8, //
                                  size,
                                  wordsPerElement * 64,
                                  StructPointer.dataSize(structPtr) * 64,
                                  StructPointer.ptrCount(structPtr),
                                  nestingLimit - 1);
        }
        case FieldSize.VOID : break;
        default :
            throw new Error("unrecognized element size");
        }

        throw new Error();
    }

    public static Text.Reader readTextPointer(SegmentReader segment,
                                              int refOffset) {
        long ref = WirePointer.get(segment.buffer, refOffset);
        int ptrOffset = WirePointer.target(refOffset, ref);
        int listPtr = WirePointer.listPointer(ref);
        int size = ListPointer.elementCount(listPtr);

        if (WirePointer.kind(ref) != WirePointer.LIST) {
            throw new DecodeException("Message contains non-list pointer where text was expected.");
        }

        if (ListPointer.elementSize(listPtr) != FieldSize.BYTE) {
            throw new DecodeException("Message contains list pointer of non-bytes where text was expected.");
        }

        // TODO bounds check?

        if (size == 0 || segment.buffer.get(8 * ptrOffset + size - 1) != 0) {
            throw new DecodeException("Message containts text that is not NUL-terminated.");
        }

        return new Text.Reader(segment.buffer, ptrOffset, size - 1);
    }
}
