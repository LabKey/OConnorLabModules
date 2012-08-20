#!/usr/bin/perl

use strict;
use warnings;
use File::Copy;
use File::Spec;
use LWP::UserAgent;
use HTTP::Request;
use JSON;

#the directory that will be scanned for completed illumina runs
my $sourceDir = '';

#the destination where completed runs will be copied
my $destinationDir = '';

#the labkey container where the run should be imported
my $containerPath = '';

#the baseUrl to the LabKey Server
my $baseUrl = 'http://localhost:8080/labkey/';

#the container holding Illumina runs
my $$containerPath = '/Illumina';

###############################
##Do not edit below this point
my $xml_filename = 'complete.xml';

if (!-e $sourceDir){
    die "Source directory $sourceDir does not exist";
}

if (!-e $destinationDir){
    die "Destination directory $destinationDir does not exist";
}

opendir(SOURCE, $sourceDir);
@files = readdir(SOURCE);

foreach my $file (@files){
    if (-d $file){
        print "file: $file\n";
        processDirectory($file);
    }
}

close SOURCE;


sub processDirectory(){
    my $file = shift;
print "XML: " . $xml_filename . "\n";
    my $xml = File::Spec::catfile($file, $xml_filename);

    if (-e $xml){
        print "xml exists";
    }
    else {
        print "xml does not exist";
        return;
    }

    #copy data to final location
    my $source = File::Spec::catfile($sourceDir, $file);
    my $dest = File::Spec::catfile($destinationDir, $file);
    move($source, $dest) || die "Unable to move directory from $source to $dest";

    #TODO: find CSV file
    my $csv = 'foo.csv';

    #pipeline import
	my $url = URI->new(
		_normalizeSlash($baseurl)
	  . 'pipeline/'
	  . _normalizeSlash($containerPath)
	  . 'startAnalysis.api?'
  	);

    my $data = {
        taskId => 'task',
        saveProtocol => 0,
        containerPath => $containerPath,
        path => '.',
        files => $csv,
        protocolName => $csv . ' Import'
    }
    my $json_obj = JSON->new->utf8->encode($data);

	my $req = new HTTP::Request;
	$req->method('POST');
	$req->url($url);
	$req->content_type('application/json');
	$req->content($json_obj);
	$req->authorization_basic( $$lk_config{'login'}, $$lk_config{'password'} );

	my $ua = new LWP::UserAgent;
	$ua->agent("Perl API Client/1.0");
	my $response = $ua->request($request);

	# Simple error checking
	if ( $response->is_error ) {
		croak($response->status_line);
	}

    #TODO: unwanted delete files?


}

sub _normalizeSlash(){
	my $containerPath = shift;

	$containerPath =~ s/^\///;
	$containerPath =~ s/\/$//;
	$containerPath .= '/';
	return $containerPath;
}
