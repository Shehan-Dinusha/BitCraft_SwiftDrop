export const formatCoordinates=(lat:number,lng:number)=>`${lat.toFixed(5)}, ${lng.toFixed(5)}`;export const formatDistance=(m:number)=>m<1000?`${Math.round(m)} m`:`${(m/1000).toFixed(1)} km`;
