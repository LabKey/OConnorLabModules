package org.labkey.genotyping.sequences;

import net.sf.picard.fastq.*;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.Pair;
import org.labkey.genotyping.GenotypingRun;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/18/12
 * Time: 11:35 AM
 */
public class IlluminaFastqParser
{
    private GenotypingRun _run;
    private List<Integer> _sampleList;
    private File[] _files;
    private Map<Pair<Integer, Integer>, Pair<File, net.sf.picard.fastq.FastqWriter>> _fileMap;
    private FastqWriterFactory _writerFactory = new FastqWriterFactory();
    private int _parsedReads = 0;
    private Logger _logger;

    public IlluminaFastqParser (GenotypingRun run, List<Integer> sampleList, Logger logger, File... files)
    {
        _run = run;
        _sampleList = sampleList;
        _files = files;
        _logger = logger;
    }

    public Map<Pair<Integer, Integer>, File> parseFastqFiles () throws PipelineJobException
    {
        _fileMap = new HashMap<Pair<Integer, Integer>, Pair<File, net.sf.picard.fastq.FastqWriter>>();
        FastqReader reader;

        for (File f : _files)
        {
            try
            {
                _logger.info("Beginning to parse file: " + f.getName());
                _parsedReads = 0;

                reader = new FastqReader(f);
                net.sf.picard.fastq.FastqWriter writer;
                while(reader.hasNext())
                {
                    FastqRecord fq = reader.next();
                    String header = fq.getReadHeader();
                    IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header);
                    int sampleIdx = parsedHeader.getSampleNum();

                    writer = getWriter(sampleIdx, f, parsedHeader.getPairNumber());
                    if(writer != null)
                        writer.write(fq);

                    _parsedReads++;
                    if (0 == _parsedReads % 25000)
                        logReadsProgress(_parsedReads);
                }

                if (0 != _parsedReads % 25000)
                    logReadsProgress(_parsedReads);

                _logger.info("Finished parsing file: " + f.getName());
                reader.close();

            }
            catch (Exception e)
            {
                for (Pair<Integer, Integer> key :_fileMap.keySet())
                {
                    _fileMap.get(key).getValue().close();
                }

                throw new PipelineJobException(e);
            }
        }

        Map<Pair<Integer, Integer>, File> outputs = new HashMap<Pair<Integer, Integer>, File>();
        for (Pair<Integer, Integer> key :_fileMap.keySet())
        {
            _fileMap.get(key).getValue().close();
            outputs.put(key, _fileMap.get(key).getKey());
        }

        return outputs;
    }

    private void logReadsProgress(int count)
    {
        String formattedCount = Formats.commaf0.format(count);
        _logger.info(formattedCount + " reads processed");
    }

    private net.sf.picard.fastq.FastqWriter getWriter (int sampleIdx, File parentFile, int pairNumber) throws IOException, PipelineJobException
    {
        if(sampleIdx > _sampleList.size())
        {
            throw new PipelineJobException("The CSV input has more samples than expected");
        }

        //NOTE: sampleIdx is 1-based and the arrayList is 0-based, so we need to subtract 1
        //the element at position 0 represent control reads and those not mapped to a sample
        int sampleId = _sampleList.get(sampleIdx);
        Pair<Integer, Integer> sampleKey = Pair.of(sampleId, pairNumber);
        if (_fileMap.containsKey(sampleKey))
        {
            return _fileMap.get(sampleKey).getValue();
        }
        else
        {
            String name = FileUtil.getBaseName(_run.getFileName()) + "-R" + pairNumber + "-" + (sampleId == 0 ? "Control" : sampleId) + ".fastq";
            File newFile = new File(parentFile.getParentFile(), name);
            newFile.createNewFile();
            net.sf.picard.fastq.FastqWriter writer = _writerFactory.newWriter(newFile);

            _fileMap.put(Pair.of(sampleId, pairNumber), Pair.of(newFile, writer));
            return writer;
        }
    }

    public class IlluminaReadHeader
    {
        private String _instrument;
        private int _runId;
        private String _flowCellId;
        private int _flowCellLane;
        private int _tileNumber;
        private int _xCoord;
        private int _yCoord;
        private int _pairNumber;
        private boolean _failedFilter;
        private int _controlBits;
        private int _sampleNum;

        public IlluminaReadHeader (String header) throws IllegalArgumentException
        {
            try
            {
                String[] h = header.split(":| ");

                if(h.length < 10)
                    throw new IllegalArgumentException("Improperly formatted header");

                _instrument = h[0];
                _runId = Integer.parseInt(h[1]);
                _flowCellId = h[2];
                _flowCellLane = Integer.parseInt(h[3]);
                _tileNumber = Integer.parseInt(h[4]);
                _xCoord = Integer.parseInt(h[5]);
                _yCoord = Integer.parseInt(h[6]);
                _pairNumber = Integer.parseInt(h[7]);
                setFailedFilter(h[8]);
                _controlBits = Integer.parseInt(h[9]);
                _sampleNum = Integer.parseInt(h[10]);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        public String getInstrument()
        {
            return _instrument;
        }

        public void setInstrument(String instrument)
        {
            _instrument = instrument;
        }

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public String getFlowCellId()
        {
            return _flowCellId;
        }

        public void setFlowCellId(String flowCellId)
        {
            _flowCellId = flowCellId;
        }

        public int getFlowCellLane()
        {
            return _flowCellLane;
        }

        public void setFlowCellLane(int flowCellLane)
        {
            _flowCellLane = flowCellLane;
        }

        public int getTileNumber()
        {
            return _tileNumber;
        }

        public void setTileNumber(int tileNumber)
        {
            _tileNumber = tileNumber;
        }

        public int getxCoord()
        {
            return _xCoord;
        }

        public void setxCoord(int xCoord)
        {
            _xCoord = xCoord;
        }

        public int getyCoord()
        {
            return _yCoord;
        }

        public void setyCoord(int yCoord)
        {
            _yCoord = yCoord;
        }

        public int getPairNumber()
        {
            return _pairNumber;
        }

        public void setPairNumber(int pairNumber)
        {
            _pairNumber = pairNumber;
        }

        public boolean isFailedFilter()
        {
            return _failedFilter;
        }

        public void setFailedFilter(boolean failedFilter)
        {
            _failedFilter = failedFilter;
        }

        public void setFailedFilter(String failedFilter)
        {
            _failedFilter = "Y".equals(failedFilter) ? true : false;
        }

        public int getControlBits()
        {
            return _controlBits;
        }

        public void setControlBits(int controlBits)
        {
            _controlBits = controlBits;
        }

        public int getSampleNum()
        {
            return _sampleNum;
        }

        public void setSampleNum(int sampleNum)
        {
            this._sampleNum = sampleNum;
        }
    }
}


